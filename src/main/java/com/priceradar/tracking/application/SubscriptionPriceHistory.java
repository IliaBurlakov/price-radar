package com.priceradar.tracking.application;

import com.priceradar.pricing.domain.RubleAmount;

import java.time.Instant;
import java.util.Optional;

public final class SubscriptionPriceHistory {

    private final Optional<RubleAmount> minimumPrice;
    private final Optional<RubleAmount> latestPrice;
    private final Optional<Instant> latestObservedAt;

    public SubscriptionPriceHistory(
            Optional<RubleAmount> minimumPrice,
            Optional<RubleAmount> latestPrice,
            Optional<Instant> latestObservedAt
    ) {
        if (minimumPrice == null || latestPrice == null || latestObservedAt == null
                || minimumPrice.isPresent() != latestPrice.isPresent()
                || latestPrice.isPresent() != latestObservedAt.isPresent()) {
            throw new IllegalArgumentException("subscription price history fields are inconsistent");
        }
        this.minimumPrice = minimumPrice;
        this.latestPrice = latestPrice;
        this.latestObservedAt = latestObservedAt;
    }

    public static SubscriptionPriceHistory empty() {
        return new SubscriptionPriceHistory(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Optional<RubleAmount> getMinimumPrice() {
        return minimumPrice;
    }

    public Optional<RubleAmount> getLatestPrice() {
        return latestPrice;
    }

    public Optional<Instant> getLatestObservedAt() {
        return latestObservedAt;
    }
}
