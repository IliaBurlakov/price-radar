package com.priceradar.telegram.application;

import java.util.UUID;

public final class TrackingCallbackData {

    public enum Action {
        TRACK_ANY_DECREASE,
        TRACK_TARGET,
        CANCEL_TARGET
    }

    private final Action action;
    private final UUID quoteSnapshotId;

    TrackingCallbackData(Action action, UUID quoteSnapshotId) {
        if (action == null || quoteSnapshotId == null) {
            throw new IllegalArgumentException("tracking callback fields must not be null");
        }
        this.action = action;
        this.quoteSnapshotId = quoteSnapshotId;
    }

    public Action getAction() {
        return action;
    }

    public UUID getQuoteSnapshotId() {
        return quoteSnapshotId;
    }
}
