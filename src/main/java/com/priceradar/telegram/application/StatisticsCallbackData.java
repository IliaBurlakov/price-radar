package com.priceradar.telegram.application;

import com.priceradar.statistics.domain.StatisticsPeriod;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class StatisticsCallbackData {

    private final UUID subscriptionId;
    private final StatisticsPeriod period;

    private StatisticsCallbackData(UUID subscriptionId, StatisticsPeriod period) {
        this.subscriptionId = subscriptionId;
        this.period = period;
    }

    public static String encode(UUID subscriptionId, StatisticsPeriod period) {
        if (subscriptionId == null || period == null) {
            throw new IllegalArgumentException("statistics callback fields must not be null");
        }
        return action(period) + ":" + subscriptionId;
    }

    public static Optional<StatisticsCallbackData> parse(String data) {
        if (data == null) {
            return Optional.empty();
        }
        int separator = data.indexOf(':');
        if (separator <= 0 || separator == data.length() - 1) {
            return Optional.empty();
        }
        Optional<StatisticsPeriod> period = period(data.substring(0, separator));
        if (period.isEmpty()) {
            return Optional.empty();
        }
        String rawId = data.substring(separator + 1);
        try {
            UUID subscriptionId = UUID.fromString(rawId);
            if (!subscriptionId.toString().equals(rawId.toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            return Optional.of(new StatisticsCallbackData(subscriptionId, period.get()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public StatisticsPeriod getPeriod() {
        return period;
    }

    private static String action(StatisticsPeriod period) {
        return switch (period) {
            case LAST_7_DAYS -> "SHOW_STATS_7D";
            case LAST_30_DAYS -> "SHOW_STATS_30D";
            case LAST_365_DAYS -> "SHOW_STATS_365D";
            case ALL_TIME -> "SHOW_STATS_ALL";
        };
    }

    private static Optional<StatisticsPeriod> period(String action) {
        return switch (action) {
            case "SHOW_STATS_7D" -> Optional.of(StatisticsPeriod.LAST_7_DAYS);
            case "SHOW_STATS_30D" -> Optional.of(StatisticsPeriod.LAST_30_DAYS);
            case "SHOW_STATS_365D" -> Optional.of(StatisticsPeriod.LAST_365_DAYS);
            case "SHOW_STATS_ALL" -> Optional.of(StatisticsPeriod.ALL_TIME);
            default -> Optional.empty();
        };
    }
}
