package com.priceradar.telegram.application;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class ShowLastKnownCallbackData {

    private static final String PREFIX = "SHOW_LAST_KNOWN:";

    private final UUID subscriptionId;

    private ShowLastKnownCallbackData(UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public static String encode(UUID subscriptionId) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId must not be null");
        }
        return PREFIX + subscriptionId;
    }

    public static Optional<ShowLastKnownCallbackData> parse(String data) {
        if (data == null || !data.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rawId = data.substring(PREFIX.length());
        try {
            UUID subscriptionId = UUID.fromString(rawId);
            if (!subscriptionId.toString().equals(rawId.toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            return Optional.of(new ShowLastKnownCallbackData(subscriptionId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }
}
