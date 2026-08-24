package com.priceradar.notification.application;

import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDecisionServiceTest {

    private final NotificationDecisionService service = new NotificationDecisionService();
    private final UUID watchTargetId = UUID.randomUUID();
    private final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void priceReturningToKnownMinimumDoesNotNotify() {
        Subscription subscription = anyDecreaseSubscription(43_400, createdAt);

        NotificationDecisionResult increase = service.evaluate(
                subscription,
                observation(49_600, createdAt.plusSeconds(60))
        );
        NotificationDecisionResult returnToMinimum = service.evaluate(
                increase.getSubscription(),
                observation(43_400, createdAt.plusSeconds(120))
        );

        assertThat(increase.getNotificationIntent()).isEmpty();
        assertThat(returnToMinimum.getNotificationIntent()).isEmpty();
        assertThat(returnToMinimum.getSubscription().getNotificationReferencePrice())
                .contains(RubleAmount.ofMinorUnits(43_400));
        assertThat(returnToMinimum.getSubscription().getLastProcessedPriceObservedAt())
                .contains(createdAt.plusSeconds(120));
    }

    @Test
    void priceBelowKnownMinimumNotifiesOnce() {
        Subscription subscription = anyDecreaseSubscription(43_400, createdAt);

        NotificationDecisionResult increase = service.evaluate(
                subscription,
                observation(49_600, createdAt.plusSeconds(60))
        );
        NotificationDecisionResult newMinimum = service.evaluate(
                increase.getSubscription(),
                observation(41_000, createdAt.plusSeconds(120))
        );

        assertThat(increase.getNotificationIntent()).isEmpty();
        assertThat(newMinimum.getNotificationIntent()).get().satisfies(intent -> {
            assertThat(intent.getPreviousPrice())
                    .contains(RubleAmount.ofMinorUnits(43_400));
            assertThat(intent.getCurrentPrice())
                    .isEqualTo(RubleAmount.ofMinorUnits(41_000));
        });
        assertThat(newMinimum.getSubscription().getNotificationReferencePrice())
                .contains(RubleAmount.ofMinorUnits(41_000));
    }

    @Test
    void eachNewSubscriptionMinimumNotifies() {
        Subscription subscription = anyDecreaseSubscription(43_400, createdAt);

        NotificationDecisionResult firstMinimum = service.evaluate(
                subscription,
                observation(41_000, createdAt.plusSeconds(60))
        );
        NotificationDecisionResult increase = service.evaluate(
                firstMinimum.getSubscription(),
                observation(42_000, createdAt.plusSeconds(120))
        );
        NotificationDecisionResult secondMinimum = service.evaluate(
                increase.getSubscription(),
                observation(40_500, createdAt.plusSeconds(180))
        );

        assertThat(firstMinimum.getNotificationIntent()).get().satisfies(intent -> {
            assertThat(intent.getPreviousPrice())
                    .contains(RubleAmount.ofMinorUnits(43_400));
            assertThat(intent.getCurrentPrice())
                    .isEqualTo(RubleAmount.ofMinorUnits(41_000));
        });
        assertThat(increase.getNotificationIntent()).isEmpty();
        assertThat(secondMinimum.getNotificationIntent()).get().satisfies(intent -> {
            assertThat(intent.getPreviousPrice())
                    .contains(RubleAmount.ofMinorUnits(41_000));
            assertThat(intent.getCurrentPrice())
                    .isEqualTo(RubleAmount.ofMinorUnits(40_500));
        });
    }

    @Test
    void staleObservationCannotChangeReferenceOrCreateNotification() {
        Subscription subscription = anyDecreaseSubscription(
                43_400,
                createdAt.plusSeconds(120)
        );

        NotificationDecisionResult stale = service.evaluate(
                subscription,
                observation(41_000, createdAt.plusSeconds(60))
        );

        assertThat(stale.isStateChanged()).isFalse();
        assertThat(stale.getNotificationIntent()).isEmpty();
        assertThat(stale.getSubscription().getNotificationReferencePrice())
                .contains(RubleAmount.ofMinorUnits(43_400));
        assertThat(stale.getSubscription().getLastProcessedPriceObservedAt())
                .contains(createdAt.plusSeconds(120));
    }

    @Test
    void staleObservationCannotRearmReachedThreshold() {
        Subscription subscription = targetSubscription(
                ThresholdState.REACHED_NOTIFIED,
                createdAt.plusSeconds(120)
        );

        NotificationDecisionResult stale = service.evaluate(
                subscription,
                observation(9_000, createdAt.plusSeconds(60))
        );
        NotificationDecisionResult current = service.evaluate(
                stale.getSubscription(),
                observation(8_400, createdAt.plusSeconds(180))
        );

        assertThat(stale.isStateChanged()).isFalse();
        assertThat(stale.getSubscription().getThresholdState())
                .isEqualTo(ThresholdState.REACHED_NOTIFIED);
        assertThat(current.getNotificationIntent()).isEmpty();
        assertThat(current.getSubscription().getThresholdState())
                .isEqualTo(ThresholdState.REACHED_NOTIFIED);
        assertThat(current.getSubscription().getThresholdObservedAt())
                .contains(createdAt.plusSeconds(180));
    }

    @Test
    void thresholdRearmsOnlyAfterNewerPriceRisesAboveTarget() {
        Subscription subscription = targetSubscription(
                ThresholdState.REACHED_NOTIFIED,
                createdAt.plusSeconds(60)
        );

        NotificationDecisionResult rearmed = service.evaluate(
                subscription,
                observation(9_000, createdAt.plusSeconds(120))
        );
        NotificationDecisionResult reachedAgain = service.evaluate(
                rearmed.getSubscription(),
                observation(8_400, createdAt.plusSeconds(180))
        );

        assertThat(rearmed.getSubscription().getThresholdState())
                .isEqualTo(ThresholdState.ABOVE_TARGET);
        assertThat(rearmed.getNotificationIntent()).isEmpty();
        assertThat(reachedAgain.getSubscription().getThresholdState())
                .isEqualTo(ThresholdState.REACHED_NOTIFIED);
        assertThat(reachedAgain.getNotificationIntent()).isPresent();
    }

    private Subscription anyDecreaseSubscription(long referencePrice, Instant observedAt) {
        return new Subscription(
                UUID.randomUUID(),
                UUID.randomUUID(),
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                Optional.of(RubleAmount.ofMinorUnits(referencePrice)),
                Optional.of(observedAt),
                ThresholdState.NOT_APPLICABLE,
                Optional.empty(),
                SubscriptionStatus.ACTIVE,
                createdAt,
                Optional.empty(),
                0
        );
    }

    private Subscription targetSubscription(ThresholdState state, Instant observedAt) {
        return new Subscription(
                UUID.randomUUID(),
                UUID.randomUUID(),
                watchTargetId,
                NotificationMode.TARGET_PRICE,
                Optional.of(RubleAmount.ofMinorUnits(8_500)),
                Optional.empty(),
                Optional.empty(),
                state,
                Optional.of(observedAt),
                SubscriptionStatus.ACTIVE,
                createdAt,
                Optional.empty(),
                0
        );
    }

    private NotificationObservation observation(long price, Instant observedAt) {
        return new NotificationObservation(
                UUID.randomUUID(),
                watchTargetId,
                new InterpretedPrice(
                        Optional.of(RubleAmount.ofMinorUnits(price)),
                        Optional.empty(),
                        Optional.of(PriceSource.PRODUCT),
                        SnapshotStatus.REGULAR_PRICE
                ),
                observedAt
        );
    }
}
