package com.priceradar.scheduler.application;

import com.priceradar.tracking.domain.WatchKey;

import java.time.Instant;
import java.util.UUID;

public final class DueWatchTarget {

    private final UUID watchTargetId;
    private final UUID productId;
    private final WatchKey watchKey;
    private final Instant nextCheckAt;

    public DueWatchTarget(
            UUID watchTargetId,
            UUID productId,
            WatchKey watchKey,
            Instant nextCheckAt
    ) {
        if (watchTargetId == null || productId == null || watchKey == null || nextCheckAt == null) {
            throw new IllegalArgumentException("due watch target fields must not be null");
        }
        this.watchTargetId = watchTargetId;
        this.productId = productId;
        this.watchKey = watchKey;
        this.nextCheckAt = nextCheckAt;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }

    public UUID getProductId() {
        return productId;
    }

    public WatchKey getWatchKey() {
        return watchKey;
    }

    public Instant getNextCheckAt() {
        return nextCheckAt;
    }
}
