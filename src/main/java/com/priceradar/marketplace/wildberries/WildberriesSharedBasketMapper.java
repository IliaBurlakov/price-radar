package com.priceradar.marketplace.wildberries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceradar.sharedbasket.application.SharedBasket;
import com.priceradar.sharedbasket.application.SharedBasketFailure;
import com.priceradar.sharedbasket.application.SharedBasketFailureCode;
import com.priceradar.sharedbasket.application.SharedBasketItem;
import com.priceradar.sharedbasket.application.SharedBasketProviderResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WildberriesSharedBasketMapper {

    private static final int MAX_ITEMS = 500;
    private final ObjectMapper objectMapper;

    public WildberriesSharedBasketMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public SharedBasketProviderResult map(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return failure(SharedBasketFailureCode.MALFORMED_RESPONSE, "Shared basket response is empty");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            return failure(SharedBasketFailureCode.MALFORMED_RESPONSE, "Shared basket response is not valid JSON");
        }
        JsonNode items = root.path("items");
        if (!root.isObject() || !items.isArray() || items.size() > MAX_ITEMS) {
            return failure(SharedBasketFailureCode.SCHEMA_VIOLATION, "Shared basket response has an invalid items field");
        }

        List<SharedBasketItem> mapped = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.path("nmId").isIntegralNumber()
                    || !item.path("chrtId").isIntegralNumber()
                    || !item.path("quantity").isIntegralNumber()
                    || !item.path("nmId").canConvertToLong()
                    || !item.path("chrtId").canConvertToLong()
                    || !item.path("quantity").canConvertToInt()
                    || item.path("nmId").longValue() <= 0
                    || item.path("chrtId").longValue() <= 0
                    || item.path("quantity").intValue() <= 0) {
                return failure(SharedBasketFailureCode.SCHEMA_VIOLATION, "Shared basket item has invalid identifiers or quantity");
            }
            mapped.add(new SharedBasketItem(
                    item.path("nmId").longValue(),
                    item.path("chrtId").longValue(),
                    item.path("quantity").intValue()
            ));
        }
        return SharedBasketProviderResult.success(new SharedBasket(mapped));
    }

    private SharedBasketProviderResult failure(SharedBasketFailureCode code, String message) {
        return SharedBasketProviderResult.failure(new SharedBasketFailure(code, message));
    }
}
