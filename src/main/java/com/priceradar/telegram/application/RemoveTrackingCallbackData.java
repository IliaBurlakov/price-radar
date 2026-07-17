package com.priceradar.telegram.application;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class RemoveTrackingCallbackData {

    private static final String PREFIX = "REMOVE_TRACKING:";

    private final UUID subscriptionId;

    private RemoveTrackingCallbackData(UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public static Optional<RemoveTrackingCallbackData> parse(String data) {
        if (data == null || !data.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rawId = data.substring(PREFIX.length());
        try {
            UUID subscriptionId = UUID.fromString(rawId);
            if (!subscriptionId.toString().equals(rawId.toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            return Optional.of(new RemoveTrackingCallbackData(subscriptionId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }
}
