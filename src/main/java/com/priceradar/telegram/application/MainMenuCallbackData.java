package com.priceradar.telegram.application;

import java.util.Optional;

public final class MainMenuCallbackData {

    private static final String PREFIX = "MENU:";

    public enum Action {
        HOME,
        ADD_PRODUCT,
        IMPORT_BASKET,
        TRACKED_ITEMS,
        REGION,
        HELP,
        FEEDBACK
    }

    private MainMenuCallbackData() {
    }

    public static String encode(Action action) {
        if (action == null) {
            throw new IllegalArgumentException("menu action must not be null");
        }
        return PREFIX + action.name();
    }

    public static Optional<Action> parse(String data) {
        if (data == null || !data.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Action.valueOf(data.substring(PREFIX.length())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
