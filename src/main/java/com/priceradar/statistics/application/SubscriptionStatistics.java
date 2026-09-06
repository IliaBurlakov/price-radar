package com.priceradar.statistics.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.statistics.domain.StatisticsPeriod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class SubscriptionStatistics {

    private final UUID subscriptionId;
    private final StatisticsPeriod period;
    private final Instant effectivePeriodStart;
    private final Instant effectivePeriodEnd;
    private final ObservedPriceStatistics observedPrices;

    public SubscriptionStatistics(
            UUID subscriptionId,
            StatisticsPeriod period,
            Instant effectivePeriodStart,
            Instant effectivePeriodEnd,
            ObservedPriceStatistics observedPrices
    ) {
        if (subscriptionId == null || period == null || effectivePeriodStart == null
                || effectivePeriodEnd == null || observedPrices == null) {
            throw new IllegalArgumentException("subscription statistics fields must not be null");
        }
        if (effectivePeriodStart.isAfter(effectivePeriodEnd)) {
            throw new IllegalArgumentException("statistics period start must not be after end");
        }
        this.subscriptionId = subscriptionId;
        this.period = period;
        this.effectivePeriodStart = effectivePeriodStart;
        this.effectivePeriodEnd = effectivePeriodEnd;
        this.observedPrices = observedPrices;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public StatisticsPeriod getPeriod() {
        return period;
    }

    public Instant getEffectivePeriodStart() {
        return effectivePeriodStart;
    }

    public Instant getEffectivePeriodEnd() {
        return effectivePeriodEnd;
    }

    public long getObservationCount() {
        return observedPrices.getObservationCount();
    }

    public Optional<RubleAmount> getMinimumPrice() {
        return observedPrices.getMinimumPrice();
    }

    public Optional<Instant> getMinimumObservedAt() {
        return observedPrices.getMinimumObservedAt();
    }

    public Optional<RubleAmount> getMaximumPrice() {
        return observedPrices.getMaximumPrice();
    }

    public Optional<RubleAmount> getFirstPrice() {
        return observedPrices.getFirstPrice();
    }

    public Optional<RubleAmount> getLatestPrice() {
        return observedPrices.getLatestPrice();
    }

    public Optional<Long> getPriceChangeMinorUnits() {
        return observedPrices.getPriceChangeMinorUnits();
    }

    public Optional<BigDecimal> getPriceChangePercent() {
        return observedPrices.getPriceChangePercent();
    }

    public Optional<RubleAmount> getLatestPriceDifferenceFromMinimum() {
        return observedPrices.getLatestPriceDifferenceFromMinimum();
    }

    public boolean hasData() {
        return observedPrices.getObservationCount() > 0;
    }
}
