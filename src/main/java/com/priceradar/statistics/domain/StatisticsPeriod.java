package com.priceradar.statistics.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public enum StatisticsPeriod {

    LAST_1_DAY(1),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    LAST_365_DAYS(365),
    ALL_TIME(null);

    private final Integer days;

    StatisticsPeriod(Integer days) {
        this.days = days;
    }

    public boolean isAvailable(Instant trackingStartedAt, Instant now) {
        validateBounds(trackingStartedAt, now);
        return days == null || !trackingStartedAt.isAfter(now.minus(days, ChronoUnit.DAYS));
    }

    public Instant effectiveStart(Instant subscriptionStartedAt, Instant now) {
        validateBounds(subscriptionStartedAt, now);
        if (days == null) {
            return subscriptionStartedAt;
        }
        Instant intervalStart = now.minus(days, ChronoUnit.DAYS);
        return subscriptionStartedAt.isAfter(intervalStart)
                ? subscriptionStartedAt
                : intervalStart;
    }

    private void validateBounds(Instant startedAt, Instant now) {
        if (startedAt == null || now == null) {
            throw new IllegalArgumentException("statistics period bounds must not be null");
        }
        if (startedAt.isAfter(now)) {
            throw new IllegalArgumentException("statistics period start must not be after now");
        }
    }
}
