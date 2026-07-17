package com.priceradar.marketplace.application;

import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.product.application.VariantOption;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MarketplaceProductDetails {
    private final Marketplace marketplace;
    private final String externalProductId;
    private final Optional<String> title;
    private final Optional<String> brand;
    private final List<VariantOption> variantOptions;
    private final Map<String, ProviderPriceFields> priceFieldsByVariantKey;

    public MarketplaceProductDetails(Marketplace marketplace, String externalProductId,
                                     Optional<String> title, Optional<String> brand,
                                     List<VariantOption> variantOptions,
                                     Map<String, ProviderPriceFields> priceFieldsByVariantKey) {
        if (marketplace == null)
            throw new IllegalArgumentException("marketplace must not be null!");
        if (externalProductId == null)
            throw new IllegalArgumentException("externalProductId must not be null!");
        String normalizedExternalProductId = externalProductId.trim();
        if (normalizedExternalProductId.isBlank())
            throw new IllegalArgumentException("externalProductId must not be blank!");
        if (title == null)
            throw new IllegalArgumentException("title must not be null!");
        if (brand == null)
            throw new IllegalArgumentException("brand must not be null!");

        if (variantOptions == null)
            throw new IllegalArgumentException("variantOptions must not be null!");
        for (VariantOption variantOption : variantOptions) {
            if (variantOption == null) {
                throw new IllegalArgumentException("variantOptions must not contain null elements!");
            }
        }

        if (priceFieldsByVariantKey == null)
            throw new IllegalArgumentException("priceFieldsByVariantKey must not be null!");
        for (Map.Entry<String, ProviderPriceFields> entry : priceFieldsByVariantKey.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("priceFieldsByVariantKey must not contain null keys!");
            }

            if (entry.getValue() == null) {
                throw new IllegalArgumentException("priceFieldsByVariantKey must not contain null values!");
            }
        }

        this.marketplace = marketplace;
        this.externalProductId = normalizedExternalProductId;
        this.title = title;
        this.brand = brand;
        this.variantOptions = List.copyOf(variantOptions);
        this.priceFieldsByVariantKey = Map.copyOf(priceFieldsByVariantKey);
    }

    public String getExternalProductId() {
        return externalProductId;
    }

    public Marketplace getMarketplace() {
        return marketplace;
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

    public Map<String, ProviderPriceFields> getPriceFieldsByVariantKey() {
        return priceFieldsByVariantKey;
    }
}
