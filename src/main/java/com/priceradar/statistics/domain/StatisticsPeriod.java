package com.priceradar.statistics.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public enum StatisticsPeriod {

    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    LAST_365_DAYS(365),
    ALL_TIME(null);

    private final Integer days;

    StatisticsPeriod(Integer days) {
        this.days = days;
    }

    public Instant effectiveStart(Instant subscriptionStartedAt, Instant now) {
        if (subscriptionStartedAt == null || now == null) {
            throw new IllegalArgumentException("statistics period bounds must not be null");
        }
        if (subscriptionStartedAt.isAfter(now)) {
            throw new IllegalArgumentException("subscriptionStartedAt must not be after now");
        }
        if (days == null) {
            return subscriptionStartedAt;
        }
        Instant intervalStart = now.minus(days, ChronoUnit.DAYS);
        return subscriptionStartedAt.isAfter(intervalStart)
                ? subscriptionStartedAt
                : intervalStart;
    }
}
