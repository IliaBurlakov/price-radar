package com.priceradar.notification.application;

import com.priceradar.pricing.application.InterpretedPrice;

import java.time.Instant;
import java.util.UUID;

public final class NotificationObservation {

    private final UUID snapshotId;
    private final UUID watchTargetId;
    private final InterpretedPrice price;
    private final Instant observedAt;

    public NotificationObservation(
            UUID snapshotId,
            UUID watchTargetId,
            InterpretedPrice price,
            Instant observedAt
    ) {
        if (snapshotId == null || watchTargetId == null || price == null || observedAt == null) {
            throw new IllegalArgumentException("notification observation fields must not be null");
        }
        this.snapshotId = snapshotId;
        this.watchTargetId = watchTargetId;
        this.price = price;
        this.observedAt = observedAt;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }

    public InterpretedPrice getPrice() {
        return price;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
