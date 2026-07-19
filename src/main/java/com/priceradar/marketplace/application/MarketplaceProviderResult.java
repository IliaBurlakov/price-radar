package com.priceradar.marketplace.application;

import java.time.Instant;
import java.util.Optional;

public final class MarketplaceProviderResult {
    private final MarketplaceProductDetails product;
    private final MarketplaceProviderFailure failure;
    private final Instant observedAt;

    private MarketplaceProviderResult(
            MarketplaceProductDetails product,
            MarketplaceProviderFailure failure,
            Instant observedAt
    ) {
        if (product == null && failure == null)
            throw new IllegalArgumentException("either product or failure must not be null!");
        if (product != null && failure != null)
            throw new IllegalArgumentException("either product or failure must be null!");
        if (product != null && observedAt == null)
            throw new IllegalArgumentException("successful result must contain observedAt!");
        if (failure != null && observedAt != null)
            throw new IllegalArgumentException("failed result must not contain observedAt!");
        this.product = product;
        this.failure = failure;
        this.observedAt = observedAt;
    }

    public static MarketplaceProviderResult success(MarketplaceProductDetails product, Instant observedAt) {
        if (product == null)
            throw new IllegalArgumentException("product must not be null!");
        if (observedAt == null)
            throw new IllegalArgumentException("observedAt must not be null!");
        return new MarketplaceProviderResult(product, null, observedAt);
    }

    public static MarketplaceProviderResult failure(MarketplaceProviderFailure failure){
        if (failure == null)
            throw new IllegalArgumentException("failure must not be null!");
        return new MarketplaceProviderResult(null, failure, null);
    }

    public boolean isSuccess() {
        return (product != null);
    }

    public Optional<MarketplaceProductDetails> getProduct() {
        if (isSuccess())
            return Optional.of(product);
        return Optional.empty();
    }

    public Optional<MarketplaceProviderFailure> getFailure() {
        if (!isSuccess())
            return Optional.of(failure);
        return Optional.empty();
    }

    public Optional<Instant> getObservedAt() {
        return Optional.ofNullable(observedAt);
    }
}
