package com.priceradar.tracking.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.PriceContext;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class SubscriptionQuoteObservation {

    private final UUID snapshotId;
    private final UUID watchTargetId;
    private final Instant observedAt;
    private final Optional<RubleAmount> regularPrice;
    private final PriceContext priceContext;

    public SubscriptionQuoteObservation(
            UUID snapshotId,
            UUID watchTargetId,
            Instant observedAt,
            Optional<RubleAmount> regularPrice,
            PriceContext priceContext
    ) {
        if (snapshotId == null || watchTargetId == null || observedAt == null
                || regularPrice == null || priceContext == null) {
            throw new IllegalArgumentException("quote observation fields must not be null");
        }
        if (regularPrice.filter(price -> price.getMinorUnits() == 0).isPresent()) {
            throw new IllegalArgumentException("regularPrice must be positive");
        }
        this.snapshotId = snapshotId;
        this.watchTargetId = watchTargetId;
        this.observedAt = observedAt;
        this.regularPrice = regularPrice;
        this.priceContext = priceContext;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }

    public Optional<RubleAmount> getRegularPrice() {
        return regularPrice;
    }

    public PriceContext getPriceContext() { return priceContext; }
}
