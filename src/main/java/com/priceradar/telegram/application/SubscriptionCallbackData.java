package com.priceradar.telegram.application;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class SubscriptionCallbackData {

    public enum Action {
        OPEN_ITEM("TRACKED_ITEM:"),
        SHOW_LAST_KNOWN("SHOW_LAST_KNOWN:"),
        SHOW_STATISTICS("SHOW_STATS_MENU:"),
        SHOW_NOTIFICATION_CONDITION("SHOW_CONDITION:"),
        SET_ANY_DECREASE("SET_MINIMUM:"),
        EDIT_TARGET_PRICE("EDIT_TARGET:"),
        CANCEL_CONDITION_EDIT("CANCEL_CONDITION:"),
        REMOVE("REMOVE_TRACKING:"),
        CONFIRM_REMOVE("CONFIRM_REMOVE:");

        private final String prefix;

        Action(String prefix) {
            this.prefix = prefix;
        }
    }

    private SubscriptionCallbackData() {
    }

    public static String encode(Action action, UUID subscriptionId) {
        if (action == null || subscriptionId == null) {
            throw new IllegalArgumentException("subscription callback fields must not be null");
        }
        return action.prefix + subscriptionId;
    }

    public static Optional<UUID> parse(Action action, String data) {
        if (action == null || data == null || !data.startsWith(action.prefix)) {
            return Optional.empty();
        }
        String rawId = data.substring(action.prefix.length());
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
