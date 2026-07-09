package com.priceradar.marketplace.wildberries;

import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.product.application.ResolvedVariant;
import com.priceradar.product.application.VariantOption;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WildberriesMappedProduct {

    private final long nmId;
    private final Optional<String> title;
    private final Optional<String> brand;
    private final List<VariantOption> variantOptions;
    private final Map<String, ProviderPriceFields> priceFieldsByVariantKey;

    public WildberriesMappedProduct(
            long nmId,
            Optional<String> title,
            Optional<String> brand,
            List<VariantOption> variantOptions,
            Map<String, ProviderPriceFields> priceFieldsByVariantKey
    ) {
        if (nmId <= 0) {
            throw new IllegalArgumentException("nmId must be positive");
        }
        this.nmId = nmId;
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.brand = Objects.requireNonNull(brand, "brand must not be null");
        this.variantOptions = copyVariantOptions(variantOptions);
        this.priceFieldsByVariantKey = copyPriceFields(priceFieldsByVariantKey);
    }

    public long getNmId() {
        return nmId;
    }

    public Optional<String> getTitle() {
        return title;
    }

    public Optional<String> getBrand() {
        return brand;
    }

    public List<VariantOption> getVariantOptions() {
        return variantOptions;
    }

    public Optional<ProviderPriceFields> findPriceFields(String variantKey) {
        Objects.requireNonNull(variantKey, "variantKey must not be null");
        return Optional.ofNullable(priceFieldsByVariantKey.get(variantKey.trim()));
    }

    public Optional<ProviderPriceFields> getNoVariantPriceFields() {
        return findPriceFields(ResolvedVariant.noVariant().getVariantKey());
    }

    private static List<VariantOption> copyVariantOptions(List<VariantOption> options) {
        Objects.requireNonNull(options, "variantOptions must not be null");
        if (options.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("variantOptions must not contain null values");
        }
        return List.copyOf(options);
    }

    private static Map<String, ProviderPriceFields> copyPriceFields(Map<String, ProviderPriceFields> priceFields) {
        Objects.requireNonNull(priceFields, "priceFieldsByVariantKey must not be null");
        LinkedHashMap<String, ProviderPriceFields> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ProviderPriceFields> entry : priceFields.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "variantKey must not be null").trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("variantKey must not be blank");
            }
            ProviderPriceFields value = Objects.requireNonNull(
                    entry.getValue(),
                    "priceFieldsByVariantKey must not contain null values"
            );
            copy.put(key, value);
        }
        return Map.copyOf(copy);
    }
}
