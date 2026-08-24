package com.priceradar.marketplace.wildberries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.product.application.VariantAttribute;
import com.priceradar.product.application.VariantOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WildberriesCardMapper {

    private static final String SIZE_ATTRIBUTE_NAME = "Size";
    private static final int MAX_TITLE_LENGTH = 500;
    private static final int MAX_BRAND_LENGTH = 255;
    private static final int MAX_VARIANT_DISPLAY_LENGTH = 255;

    private final ObjectMapper objectMapper;

    public WildberriesCardMapper() {
        this(new ObjectMapper());
    }

    public WildberriesCardMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public WildberriesMappingResult map(String rawJson, long expectedNmId) {
        if (expectedNmId <= 0) {
            throw new IllegalArgumentException("expectedNmId must be positive");
        }
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return failure(WildberriesMappingFailureCode.EMPTY_RESPONSE, "Wildberries response is empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            return failure(WildberriesMappingFailureCode.MALFORMED_JSON, "Wildberries response is not valid JSON");
        }

        Optional<JsonNode> productNode = findProductNode(root, expectedNmId);
        if (productNode.isEmpty()) {
            return failure(WildberriesMappingFailureCode.PRODUCT_NOT_FOUND, "Product was not found in response");
        }

        return mapProduct(productNode.get(), expectedNmId);
    }

    public WildberriesBatchMappingResult mapBatch(String rawJson, List<Long> expectedNmIds) {
        if (expectedNmIds == null || expectedNmIds.isEmpty()
                || expectedNmIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("expectedNmIds must contain positive ids");
        }
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return WildberriesBatchMappingResult.failure(new WildberriesMappingFailure(
                    WildberriesMappingFailureCode.EMPTY_RESPONSE,
                    "Wildberries response is empty"
            ));
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            return WildberriesBatchMappingResult.failure(new WildberriesMappingFailure(
                    WildberriesMappingFailureCode.MALFORMED_JSON,
                    "Wildberries response is not valid JSON"
            ));
        }

        Optional<JsonNode> productsNode = firstExistingArray(
                root.path("data").path("products"),
                root.path("products")
        );
        if (productsNode.isEmpty()) {
            return WildberriesBatchMappingResult.failure(new WildberriesMappingFailure(
                    WildberriesMappingFailureCode.SCHEMA_VIOLATION,
                    "Wildberries batch response does not contain products"
            ));
        }

        Map<Long, JsonNode> nodesById = new LinkedHashMap<>();
        for (JsonNode productNode : productsNode.orElseThrow()) {
            positiveLong(productNode, "id", "nmId").ifPresent(id -> nodesById.putIfAbsent(id, productNode));
        }

        Map<Long, WildberriesMappingResult> results = new LinkedHashMap<>();
        for (Long nmId : expectedNmIds) {
            JsonNode productNode = nodesById.get(nmId);
            results.put(nmId, productNode == null
                    ? failure(WildberriesMappingFailureCode.PRODUCT_NOT_FOUND, "Product was not found in response")
                    : mapProduct(productNode, nmId));
        }
        return WildberriesBatchMappingResult.success(results);
    }

    private WildberriesMappingResult mapProduct(JsonNode productNode, long expectedNmId) {
        Optional<Long> nmId = positiveLong(productNode, "id", "nmId");
        if (nmId.isEmpty()) {
            return failure(WildberriesMappingFailureCode.SCHEMA_VIOLATION, "Product id is missing or invalid");
        }
        if (nmId.get() != expectedNmId) {
            return failure(WildberriesMappingFailureCode.PRODUCT_NOT_FOUND, "Response contains another product id");
        }

        JsonNode sizes = productNode.path("sizes");
        if (sizes.isMissingNode()) {
            return mapNoVariantProduct(productNode, nmId.get());
        }
        if (!sizes.isArray()) {
            return failure(
                    WildberriesMappingFailureCode.SCHEMA_VIOLATION,
                    "Product sizes must be an array"
            );
        }
        if (sizes.isEmpty()) {
            return mapNoVariantProduct(productNode, nmId.get());
        }

        return mapSizedProduct(productNode, sizes, nmId.get());
    }

    private WildberriesMappingResult mapNoVariantProduct(JsonNode productNode, long nmId) {
        String noVariantKey = ResolvedVariant.noVariant().getVariantKey();
        ProviderPriceFields priceFields = mapPriceFields(productNode, readProductAvailability(productNode));

        Map<String, ProviderPriceFields> priceFieldsByVariant = new LinkedHashMap<>();
        priceFieldsByVariant.put(noVariantKey, priceFields);

        return WildberriesMappingResult.success(new WildberriesMappedProduct(
                nmId,
                limitedText(productNode, MAX_TITLE_LENGTH, "name", "title"),
                limitedText(productNode, MAX_BRAND_LENGTH, "brand", "brandName"),
                List.of(),
                priceFieldsByVariant
        ));
    }

    private WildberriesMappingResult mapSizedProduct(JsonNode productNode, JsonNode sizes, long nmId) {
        List<VariantOption> options = new ArrayList<>();
        Map<String, ProviderPriceFields> priceFieldsByVariant = new LinkedHashMap<>();

        for (JsonNode sizeNode : sizes) {
            Optional<Long> sizeId = positiveLong(sizeNode, "optionId", "sizeId", "chrtId", "id");
            if (sizeId.isEmpty()) {
                return failure(
                        WildberriesMappingFailureCode.SCHEMA_VIOLATION,
                        "Size option id is missing or invalid"
                );
            }

            boolean available = readSizeAvailability(sizeNode);
            ProviderPriceFields priceFields = mapPriceFields(sizeNode, available);

            List<VariantAttribute> attributes = List.of(new VariantAttribute(
                    SIZE_ATTRIBUTE_NAME,
                    limitedText(sizeNode, MAX_VARIANT_DISPLAY_LENGTH, "name", "origName", "optionName")
                            .orElse(String.valueOf(sizeId.get()))
            ));
            VariantOption option = VariantOption.wildberriesSize(
                    sizeId.get(),
                    attributes,
                    available,
                    priceFields.getProductPrice().isPresent()
            );

            if (priceFieldsByVariant.containsKey(option.getVariantKey())) {
                return failure(
                        WildberriesMappingFailureCode.SCHEMA_VIOLATION,
                        "Response contains duplicate size option id"
                );
            }

            options.add(option);
            priceFieldsByVariant.put(option.getVariantKey(), priceFields);
        }

        return WildberriesMappingResult.success(new WildberriesMappedProduct(
                nmId,
                limitedText(productNode, MAX_TITLE_LENGTH, "name", "title"),
                limitedText(productNode, MAX_BRAND_LENGTH, "brand", "brandName"),
                options,
                priceFieldsByVariant
        ));
    }

    private ProviderPriceFields mapPriceFields(JsonNode ownerNode, boolean available) {
        JsonNode priceNode = ownerNode.path("price");
        return new ProviderPriceFields(
                available,
                positiveAmount(priceNode, "product"),
                positiveAmount(priceNode, "basic")
        );
    }

    private Optional<JsonNode> findProductNode(JsonNode root, long expectedNmId) {
        Optional<JsonNode> products = firstExistingArray(
                root.path("data").path("products"),
                root.path("products")
        );
        if (products.isPresent()) {
            for (JsonNode product : products.get()) {
                Optional<Long> id = positiveLong(product, "id", "nmId");
                if (id.isPresent() && id.get() == expectedNmId) {
                    return Optional.of(product);
                }
            }
            return Optional.empty();
        }

        JsonNode product = root.path("product");
        if (product.isObject()) {
            return Optional.of(product);
        }

        if (root.isObject() && positiveLong(root, "id", "nmId").isPresent()) {
            return Optional.of(root);
        }

        return Optional.empty();
    }

    private Optional<JsonNode> firstExistingArray(JsonNode first, JsonNode second) {
        if (first.isArray()) {
            return Optional.of(first);
        }
        if (second.isArray()) {
            return Optional.of(second);
        }
        return Optional.empty();
    }

    private boolean readProductAvailability(JsonNode productNode) {
        Optional<Boolean> explicit = booleanValue(productNode, "available", "isAvailable");
        return explicit.orElse(true);
    }

    private boolean readSizeAvailability(JsonNode sizeNode) {
        Optional<Boolean> explicit = booleanValue(sizeNode, "available", "isAvailable");
        if (explicit.isPresent()) {
            return explicit.get();
        }
        JsonNode stocks = sizeNode.path("stocks");
        if (stocks.isArray()) {
            for (JsonNode stock : stocks) {
                if (positiveLong(stock, "qty").isPresent()) {
                    return true;
                }
            }
            return false;
        }
        return positiveLong(sizeNode, "quantity", "qty", "stockCount").isPresent();
    }

    private Optional<Boolean> booleanValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isBoolean()) {
                return Optional.of(value.booleanValue());
            }
        }
        return Optional.empty();
    }

    private Optional<String> text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual()) {
                String normalized = value.asText().trim();
                if (!normalized.isEmpty()) {
                    return Optional.of(normalized);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> limitedText(JsonNode node, int maxLength, String... fieldNames) {
        return text(node, fieldNames).map(value -> truncate(value, maxLength));
    }

    private String truncate(String value, int maxLength) {
        if (value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }

    private Optional<RubleAmount> positiveAmount(JsonNode node, String fieldName) {
        return positiveLong(node, fieldName).map(RubleAmount::ofMinorUnits);
    }

    private Optional<Long> positiveLong(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Optional<Long> value = positiveLong(node.path(fieldName));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<Long> positiveLong(JsonNode value) {
        if (value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0) {
            return Optional.of(value.longValue());
        }
        if (value.isTextual()) {
            try {
                long parsed = Long.parseLong(value.asText().trim());
                if (parsed > 0) {
                    return Optional.of(parsed);
                }
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private WildberriesMappingResult failure(WildberriesMappingFailureCode code, String message) {
        return WildberriesMappingResult.failure(new WildberriesMappingFailure(code, message));
    }
}
