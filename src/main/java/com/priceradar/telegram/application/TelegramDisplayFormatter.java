package com.priceradar.telegram.application;

import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.application.WalletEstimate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public final class TelegramDisplayFormatter {

    private static final String SIZE_PREFIX = "Size:";
    private static final String APPROXIMATE_PRICE_WARNING =
            "⚠️ Цена может отличаться в приложении Wildberries.";
    private static final DateTimeFormatter OBSERVED_AT_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm 'МСК'", Locale.ROOT)
            .withZone(ZoneId.of("Europe/Moscow"));
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy", Locale.ROOT)
            .withZone(ZoneId.of("Europe/Moscow"));

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

    public static String approximatePriceWarning() {
        return APPROXIMATE_PRICE_WARNING;
    }

    public static String observedAt(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant must not be null");
        }
        return OBSERVED_AT_FORMAT.format(instant);
    }

    public static String date(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant must not be null");
        }
        return DATE_FORMAT.format(instant);
    }

    public static String walletEstimate(WalletEstimate estimate) {
        if (estimate == null) {
            throw new IllegalArgumentException("estimate must not be null");
        }
        return walletAmount(estimate)
                + " (скидка " + estimate.getWalletDiscountPercent() + "%)";
    }

    public static String walletAmount(WalletEstimate estimate) {
        if (estimate == null) {
            throw new IllegalArgumentException("estimate must not be null");
        }
        return "≈ " + RublePriceFormatter.format(estimate.getAmount());
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
