package com.priceradar.statistics.application;

import com.priceradar.statistics.domain.StatisticsPeriod;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.Subscription;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SubscriptionStatisticsService {

    private final SubscriptionStore subscriptionStore;
    private final PriceStatisticsStore priceStatisticsStore;

    public SubscriptionStatisticsService(
            SubscriptionStore subscriptionStore,
            PriceStatisticsStore priceStatisticsStore
    ) {
        if (subscriptionStore == null || priceStatisticsStore == null) {
            throw new IllegalArgumentException("statistics service dependencies must not be null");
        }
        this.subscriptionStore = subscriptionStore;
        this.priceStatisticsStore = priceStatisticsStore;
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionStatistics> calculate(
            UUID userId,
            UUID subscriptionId,
            StatisticsPeriod period,
            Instant now
    ) {
        if (userId == null || subscriptionId == null || period == null || now == null) {
            throw new IllegalArgumentException("statistics query fields must not be null");
        }
        Optional<Subscription> activeSubscription = subscriptionStore.findActiveOwned(
                userId,
                subscriptionId
        );
        if (activeSubscription.isEmpty()) {
            return Optional.empty();
        }

        Subscription subscription = activeSubscription.get();
        if (!period.isAvailable(subscription.getCreatedAt(), now)) {
            return Optional.empty();
        }
        Instant effectiveStart = period.effectiveStart(
                subscription.getPriceHistoryStartedAt(),
                now
        );
        ObservedPriceStatistics observedPrices = priceStatisticsStore.calculate(
                subscription.getWatchTargetId(),
                effectiveStart,
                now
        );
        return Optional.of(new SubscriptionStatistics(
                subscription.getId(),
                period,
                effectiveStart,
                now,
                observedPrices
        ));
    }

    @Transactional(readOnly = true)
    public Optional<List<StatisticsPeriod>> findAvailablePeriods(
            UUID userId,
            UUID subscriptionId,
            Instant now
    ) {
        if (userId == null || subscriptionId == null || now == null) {
            throw new IllegalArgumentException("statistics period query fields must not be null");
        }
        return subscriptionStore.findActiveOwned(userId, subscriptionId)
                .map(subscription -> Arrays.stream(StatisticsPeriod.values())
                        .filter(period -> period.isAvailable(subscription.getCreatedAt(), now))
                        .toList());
    }
}
