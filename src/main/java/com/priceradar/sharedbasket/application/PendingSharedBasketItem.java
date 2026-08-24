package com.priceradar.sharedbasket.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PendingSharedBasketItem {

    private final int position;
    private final UUID watchTargetId;
    private final UUID snapshotId;
    private final Optional<String> title;

    public PendingSharedBasketItem(int position, UUID watchTargetId, UUID snapshotId, Optional<String> title) {
        if (position < 0 || watchTargetId == null || snapshotId == null || title == null) {
            throw new IllegalArgumentException("pending shared basket item fields are invalid");
        }
        this.position = position;
        this.watchTargetId = watchTargetId;
        this.snapshotId = snapshotId;
        this.title = title.map(String::trim).filter(value -> !value.isEmpty());
    }

    public int getPosition() { return position; }
    public UUID getWatchTargetId() { return watchTargetId; }
    public UUID getSnapshotId() { return snapshotId; }
    public Optional<String> getTitle() { return title; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PendingSharedBasketItem that)) return false;
        return watchTargetId.equals(that.watchTargetId);
    }

    @Override
    public int hashCode() { return Objects.hash(watchTargetId); }
}
