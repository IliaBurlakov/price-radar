package com.priceradar.product.application;

import java.util.UUID;

public final class PersistedResolvedQuote {

    private final UUID watchTargetId;
    private final UUID snapshotId;

    public PersistedResolvedQuote(UUID watchTargetId, UUID snapshotId) {
        if (watchTargetId == null || snapshotId == null) {
            throw new IllegalArgumentException("persisted quote identifiers must not be null");
        }
        this.watchTargetId = watchTargetId;
        this.snapshotId = snapshotId;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }
}
