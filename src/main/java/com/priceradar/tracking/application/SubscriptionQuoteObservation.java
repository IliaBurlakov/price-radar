package com.priceradar.tracking.application;

import com.priceradar.pricing.domain.RubleAmount;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class SubscriptionQuoteObservation {

    private final UUID snapshotId;
    private final UUID watchTargetId;
    private final Instant observedAt;
    private final Optional<RubleAmount> regularPrice;

    public SubscriptionQuoteObservation(
            UUID snapshotId,
            UUID watchTargetId,
            Instant observedAt,
            Optional<RubleAmount> regularPrice
    ) {
        if (snapshotId == null || watchTargetId == null || observedAt == null || regularPrice == null) {
            throw new IllegalArgumentException("quote observation fields must not be null");
        }
        if (regularPrice.filter(price -> price.getMinorUnits() == 0).isPresent()) {
            throw new IllegalArgumentException("regularPrice must be positive");
        }
        this.snapshotId = snapshotId;
        this.watchTargetId = watchTargetId;
        this.observedAt = observedAt;
        this.regularPrice = regularPrice;
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
}
