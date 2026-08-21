package com.priceradar.telegram.application;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class StatisticsMenuCallbackData {

    private static final String PREFIX = "SHOW_STATS_MENU:";

    private StatisticsMenuCallbackData() {
    }

    public static String encode(UUID subscriptionId) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId must not be null");
        }
        return PREFIX + subscriptionId;
    }

    public static Optional<UUID> parse(String data) {
        if (data == null || !data.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rawId = data.substring(PREFIX.length());
        try {
            UUID subscriptionId = UUID.fromString(rawId);
            if (!subscriptionId.toString().equals(rawId.toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            return Optional.of(subscriptionId);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
