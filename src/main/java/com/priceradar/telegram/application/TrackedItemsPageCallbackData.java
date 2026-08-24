package com.priceradar.telegram.application;

import java.util.OptionalInt;

public final class TrackedItemsPageCallbackData {

    private static final String PREFIX = "TRACKED_PAGE:";

    private TrackedItemsPageCallbackData() {
    }

    public static String encode(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        return PREFIX + pageNumber;
    }

    public static OptionalInt parse(String data) {
        if (data == null || !data.startsWith(PREFIX)) {
            return OptionalInt.empty();
        }
        try {
            int pageNumber = Integer.parseInt(data.substring(PREFIX.length()));
            return pageNumber >= 0 ? OptionalInt.of(pageNumber) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }
}
