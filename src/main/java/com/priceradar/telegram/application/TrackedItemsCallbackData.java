package com.priceradar.telegram.application;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class TrackedItemsCallbackData {

    private static final String ITEM_PREFIX = "TRACKED_ITEM:";
    private static final String PAGE_PREFIX = "TRACKED_PAGE:";

    private TrackedItemsCallbackData() {
    }

    public static String item(UUID subscriptionId) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId must not be null");
        }
        return ITEM_PREFIX + subscriptionId;
    }

    public static String page(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        return PAGE_PREFIX + pageNumber;
    }

    public static Optional<UUID> parseItem(String data) {
        if (data == null || !data.startsWith(ITEM_PREFIX)) {
            return Optional.empty();
        }
        String rawId = data.substring(ITEM_PREFIX.length());
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

    public static OptionalInt parsePage(String data) {
        if (data == null || !data.startsWith(PAGE_PREFIX)) {
            return OptionalInt.empty();
        }
        try {
            int pageNumber = Integer.parseInt(data.substring(PAGE_PREFIX.length()));
            return pageNumber >= 0 ? OptionalInt.of(pageNumber) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }
}
