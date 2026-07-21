package com.priceradar.tracking.application;

import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceContext;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class LatestSnapshotView {

    private final UUID subscriptionId;
    private final long nmId;
    private final Optional<String> title;
    private final Optional<String> brand;
    private final String canonicalUrl;
    private final Optional<String> variantDisplayName;
    private final PriceContext priceContext;
    private final Optional<InterpretedPrice> interpretedPrice;
    private final Optional<Instant> observedAt;

    public LatestSnapshotView(
            UUID subscriptionId,
            long nmId,
            Optional<String> title,
            Optional<String> brand,
            String canonicalUrl,
            Optional<String> variantDisplayName,
            PriceContext priceContext,
            Optional<InterpretedPrice> interpretedPrice,
            Optional<Instant> observedAt
    ) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId must not be null");
        }
        if (nmId <= 0) {
            throw new IllegalArgumentException("nmId must be positive");
        }
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            throw new IllegalArgumentException("canonicalUrl must not be blank");
        }
        if (title == null || brand == null || variantDisplayName == null
                || priceContext == null || interpretedPrice == null || observedAt == null) {
            throw new IllegalArgumentException("latest snapshot optional fields must not be null");
        }
        if (interpretedPrice.isPresent() != observedAt.isPresent()) {
            throw new IllegalArgumentException("snapshot price and observedAt must be set together");
        }
        this.subscriptionId = subscriptionId;
        this.nmId = nmId;
        this.title = normalize(title);
        this.brand = normalize(brand);
        this.canonicalUrl = canonicalUrl.trim();
        this.variantDisplayName = normalize(variantDisplayName);
        this.priceContext = priceContext;
        this.interpretedPrice = interpretedPrice;
        this.observedAt = observedAt;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
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

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public Optional<String> getVariantDisplayName() {
        return variantDisplayName;
    }

    public PriceContext getPriceContext() {
        return priceContext;
    }

    public Optional<InterpretedPrice> getInterpretedPrice() {
        return interpretedPrice;
    }

    public Optional<Instant> getObservedAt() {
        return observedAt;
    }

    public boolean hasSnapshot() {
        return interpretedPrice.isPresent();
    }

    private Optional<String> normalize(Optional<String> value) {
        return value.map(String::trim).filter(text -> !text.isEmpty());
    }
}
