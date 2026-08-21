package com.priceradar.telegram.application;

import java.util.Optional;

public final class TelegramDisplayFormatter {

    private static final String SIZE_PREFIX = "Size:";

    private TelegramDisplayFormatter() {
    }

    public static String region(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            throw new IllegalArgumentException("cityName must not be blank");
        }
        String normalized = cityName.trim();
        if (normalized.equalsIgnoreCase("Moscow")) {
            return "Москва";
        }
        if (normalized.equalsIgnoreCase("Novosibirsk")) {
            return "Новосибирск";
        }
        return normalized;
    }

    public static Optional<String> variant(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return Optional.empty();
        }
        String normalized = displayName.trim();
        if (normalized.equals("0")) {
            return Optional.empty();
        }
        if (startsWithIgnoreCase(normalized, SIZE_PREFIX)) {
            String value = normalized.substring(SIZE_PREFIX.length()).trim();
            if (value.isEmpty() || value.equals("0")) {
                return Optional.empty();
            }
            return Optional.of("Размер: " + value);
        }
        return Optional.of(normalized);
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
