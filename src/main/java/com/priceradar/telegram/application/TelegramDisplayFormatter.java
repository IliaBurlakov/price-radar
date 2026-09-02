package com.priceradar.telegram.application;

import com.priceradar.pricing.application.RublePriceFormatter;
import com.priceradar.pricing.application.WalletEstimate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TelegramDisplayFormatter {

    private static final DateTimeFormatter OBSERVED_AT_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm 'МСК'", Locale.ROOT)
            .withZone(ZoneId.of("Europe/Moscow"));
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy", Locale.ROOT)
            .withZone(ZoneId.of("Europe/Moscow"));

    private TelegramDisplayFormatter() {
    }

    public static String trackingCapacity(int activeSubscriptions, int limit) {
        if (activeSubscriptions < 0 || limit <= 0) {
            throw new IllegalArgumentException("subscription capacity must be valid");
        }
        int occupiedSlots = Math.min(activeSubscriptions, limit);
        int freeSlots = Math.max(0, limit - activeSubscriptions);
        return "Отслеживается: %d из %d · можно добавить ещё %d"
                .formatted(occupiedSlots, limit, freeSlots);
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
        String formatted = Stream.of(normalized.split(" / "))
                .map(String::trim)
                .map(TelegramDisplayFormatter::formatVariantAttribute)
                .flatMap(Optional::stream)
                .collect(Collectors.joining(" / "));
        return formatted.isEmpty() ? Optional.empty() : Optional.of(formatted);
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
        return RublePriceFormatter.format(estimate.getAmount());
    }

    public static String targetPriceInputPrompt() {
        return "🎯 Введите желаемую цену в рублях.\n\n"
                + "Например: 1500\n"
                + "Ответ можно отправить в течение 15 минут.";
    }

    private static Optional<String> formatVariantAttribute(String attribute) {
        if (attribute.isEmpty() || attribute.equals("0")) {
            return Optional.empty();
        }
        int separator = attribute.indexOf(':');
        if (separator < 0) {
            return Optional.of("Вариант: " + attribute);
        }

        String name = attribute.substring(0, separator).trim();
        String value = attribute.substring(separator + 1).trim();
        if (value.isEmpty() || (name.equalsIgnoreCase("size") && value.equals("0"))) {
            return Optional.empty();
        }

        String localizedName = switch (name.toLowerCase(Locale.ROOT)) {
            case "size" -> "Размер";
            case "color", "colour" -> "Цвет";
            case "memory", "storage" -> "Память";
            default -> name;
        };
        return Optional.of(localizedName + ": " + value);
    }
}
