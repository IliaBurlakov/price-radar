package com.priceradar.marketplace.application;

import java.util.Map;
import java.util.Optional;

public final class MarketplaceBatchProviderResult {

    private final Map<String, MarketplaceProviderResult> products;
    private final Optional<MarketplaceProviderFailure> failure;

    private MarketplaceBatchProviderResult(
            Map<String, MarketplaceProviderResult> products,
            Optional<MarketplaceProviderFailure> failure
    ) {
        this.products = Map.copyOf(products);
        this.failure = failure;
    }

    public static MarketplaceBatchProviderResult success(Map<String, MarketplaceProviderResult> products) {
        return new MarketplaceBatchProviderResult(products, Optional.empty());
    }

    public static MarketplaceBatchProviderResult failure(MarketplaceProviderFailure failure) {
        return new MarketplaceBatchProviderResult(Map.of(), Optional.of(failure));
    }

    public boolean isSuccess() {
        return failure.isEmpty();
    }

    public Map<String, MarketplaceProviderResult> getProducts() {
        return products;
    }

    public Optional<MarketplaceProviderFailure> getFailure() {
        return failure;
    }
}
