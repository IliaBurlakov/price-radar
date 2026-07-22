package com.priceradar.statistics.application;

import com.priceradar.statistics.domain.StatisticsPeriod;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionStatisticsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private SubscriptionStore subscriptionStore;

    @Mock
    private PriceStatisticsStore priceStatisticsStore;

    private SubscriptionStatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new SubscriptionStatisticsService(
                subscriptionStore,
                priceStatisticsStore
        );
    }

    @Test
    void intersectsRequestedIntervalWithCurrentSubscriptionPeriod() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID watchTargetId = UUID.randomUUID();
        Instant subscriptionStartedAt = NOW.minus(30, ChronoUnit.DAYS);
        Subscription subscription = activeSubscription(
                subscriptionId,
                userId,
                watchTargetId,
                subscriptionStartedAt
        );
        when(subscriptionStore.findActiveOwned(userId, subscriptionId))
                .thenReturn(Optional.of(subscription));
        when(priceStatisticsStore.calculate(
                watchTargetId,
                NOW.minus(7, ChronoUnit.DAYS),
                NOW
        )).thenReturn(ObservedPriceStatistics.empty());
        when(priceStatisticsStore.calculate(watchTargetId, subscriptionStartedAt, NOW))
                .thenReturn(ObservedPriceStatistics.empty());

        SubscriptionStatistics lastSevenDays = statisticsService.calculate(
                userId,
                subscriptionId,
                StatisticsPeriod.LAST_7_DAYS,
                NOW
        ).orElseThrow();
        SubscriptionStatistics allTime = statisticsService.calculate(
                userId,
                subscriptionId,
                StatisticsPeriod.ALL_TIME,
                NOW
        ).orElseThrow();

        assertThat(lastSevenDays.getEffectivePeriodStart())
                .isEqualTo(NOW.minus(7, ChronoUnit.DAYS));
        assertThat(allTime.getEffectivePeriodStart()).isEqualTo(subscriptionStartedAt);
        assertThat(lastSevenDays.hasData()).isFalse();
        assertThat(allTime.hasData()).isFalse();
    }

    @Test
    void doesNotQuerySharedSnapshotsWithoutAnActiveOwnedSubscription() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        when(subscriptionStore.findActiveOwned(userId, subscriptionId))
                .thenReturn(Optional.empty());

        assertThat(statisticsService.calculate(
                userId,
                subscriptionId,
                StatisticsPeriod.ALL_TIME,
                NOW
        )).isEmpty();
        verify(priceStatisticsStore, never()).calculate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private Subscription activeSubscription(
            UUID subscriptionId,
            UUID userId,
            UUID watchTargetId,
            Instant createdAt
    ) {
        return new Subscription(
                subscriptionId,
                userId,
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ThresholdState.NOT_APPLICABLE,
                Optional.empty(),
                SubscriptionStatus.ACTIVE,
                createdAt,
                Optional.empty(),
                0
        );
    }
}
