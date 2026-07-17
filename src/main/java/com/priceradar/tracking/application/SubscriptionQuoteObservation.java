package com.priceradar.tracking.application;

import com.priceradar.pricing.domain.RubleAmount;

import java.time.Instant;
import java.util.Optional;

public final class SubscriptionQuoteObservation {

    private final Instant observedAt;
    private final Optional<RubleAmount> regularPrice;

    public SubscriptionQuoteObservation(
            Instant observedAt,
            Optional<RubleAmount> regularPrice
    ) {
        if (observedAt == null || regularPrice == null) {
            throw new IllegalArgumentException("quote observation fields must not be null");
        }
        if (regularPrice.filter(price -> price.getMinorUnits() == 0).isPresent()) {
            throw new IllegalArgumentException("regularPrice must be positive");
        }
        this.observedAt = observedAt;
        this.regularPrice = regularPrice;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Optional<RubleAmount> getRegularPrice() {
        return regularPrice;
    }
}
