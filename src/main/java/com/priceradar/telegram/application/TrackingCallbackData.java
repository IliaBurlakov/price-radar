package com.priceradar.telegram.application;

import java.util.UUID;

public final class TrackingCallbackData {

    public enum Action {
        TRACK_ANY_DECREASE,
        TRACK_TARGET
    }

    private final Action action;
    private final UUID watchTargetId;

    TrackingCallbackData(Action action, UUID watchTargetId) {
        if (action == null || watchTargetId == null) {
            throw new IllegalArgumentException("tracking callback fields must not be null");
        }
        this.action = action;
        this.watchTargetId = watchTargetId;
    }

    public Action getAction() {
        return action;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }
}
