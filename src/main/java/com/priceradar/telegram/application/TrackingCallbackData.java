package com.priceradar.telegram.application;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class TrackingCallbackData {

    public enum Action {
        TRACK_ANY_DECREASE,
        TRACK_TARGET
    }

    private final Action action;
    private final UUID watchTargetId;

    private TrackingCallbackData(Action action, UUID watchTargetId) {
        this.action = action;
        this.watchTargetId = watchTargetId;
    }

    public static Optional<TrackingCallbackData> parse(String data) {
        if (data == null) {
            return Optional.empty();
        }
        int separator = data.indexOf(':');
        if (separator <= 0 || separator != data.lastIndexOf(':')) {
            return Optional.empty();
        }

        try {
            Action action = Action.valueOf(data.substring(0, separator));
            String rawId = data.substring(separator + 1);
            UUID watchTargetId = UUID.fromString(rawId);
            if (!watchTargetId.toString().equals(rawId.toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            return Optional.of(new TrackingCallbackData(action, watchTargetId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Action getAction() {
        return action;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }
}
