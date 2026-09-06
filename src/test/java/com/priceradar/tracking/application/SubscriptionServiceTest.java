package com.priceradar.tracking.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import com.priceradar.user.application.UserProfileStore;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;

class SubscriptionServiceTest {

    private final UserProfileStore userStore = mock(UserProfileStore.class);
    private final SubscriptionStore subscriptionStore = mock(SubscriptionStore.class);
    private final InitialThresholdNotificationEnqueuer thresholdNotificationEnqueuer =
            mock(InitialThresholdNotificationEnqueuer.class);
    private final SubscriptionService service = new SubscriptionService(
            userStore,
            subscriptionStore,
            thresholdNotificationEnqueuer,
            Duration.ofMinutes(15)
    );

    @Test
    void anyDecreaseStartsPriceHistoryAtInitialRegularObservation() {
        UUID userId = UUID.randomUUID();
        UUID quoteSnapshotId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-01-01T00:09:30Z");
        Instant createdAt = Instant.parse("2026-01-01T00:10:00Z");
        SubscriptionQuoteObservation observation = new SubscriptionQuoteObservation(
                quoteSnapshotId,
                UUID.randomUUID(),
                observedAt,
                Optional.of(RubleAmount.ofMinorUnits(44_500)),
                moscow().toPriceContext()
        );
        ready(userId, quoteSnapshotId, observation);
        when(subscriptionStore.create(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Subscription subscription = service.createFromQuote(
                userId,
                quoteSnapshotId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                createdAt
        ).getSubscription().orElseThrow();

        assertThat(subscription.getCreatedAt()).isEqualTo(createdAt);
        assertThat(subscription.getPriceHistoryStartedAt()).isEqualTo(observedAt);
        assertThat(subscription.getNotificationReferencePrice())
                .contains(RubleAmount.ofMinorUnits(44_500));
        assertThat(subscription.getLastProcessedPriceObservedAt()).contains(observedAt);
        verifyNoInteractions(thresholdNotificationEnqueuer);
    }

    @Test
    void targetPriceStartsPriceHistoryAtInitialRegularObservation() {
        UUID userId = UUID.randomUUID();
        UUID quoteSnapshotId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-01-01T00:09:30Z");
        Instant createdAt = Instant.parse("2026-01-01T00:10:00Z");
        SubscriptionQuoteObservation observation = new SubscriptionQuoteObservation(
                quoteSnapshotId,
                UUID.randomUUID(),
                observedAt,
                Optional.of(RubleAmount.ofMinorUnits(44_500)),
                moscow().toPriceContext()
        );
        ready(userId, quoteSnapshotId, observation);
        when(subscriptionStore.create(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Subscription subscription = service.createFromQuote(
                userId,
                quoteSnapshotId,
                NotificationMode.TARGET_PRICE,
                Optional.of(RubleAmount.ofMinorUnits(40_000)),
                createdAt
        ).getSubscription().orElseThrow();

        assertThat(subscription.getCreatedAt()).isEqualTo(createdAt);
        assertThat(subscription.getPriceHistoryStartedAt()).isEqualTo(observedAt);
        assertThat(subscription.getThresholdState()).isEqualTo(ThresholdState.ABOVE_TARGET);
        assertThat(subscription.getThresholdObservedAt()).contains(observedAt);
        assertThat(subscription.getLastProcessedPriceObservedAt()).contains(observedAt);
        verifyNoInteractions(thresholdNotificationEnqueuer);
    }

    @Test
    void subscriptionWithoutRegularObservationStartsPriceHistoryAtCreationTime() {
        UUID userId = UUID.randomUUID();
        UUID quoteSnapshotId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-01-01T00:09:30Z");
        Instant createdAt = Instant.parse("2026-01-01T00:10:00Z");
        SubscriptionQuoteObservation observation = new SubscriptionQuoteObservation(
                quoteSnapshotId,
                UUID.randomUUID(),
                observedAt,
                Optional.empty(),
                moscow().toPriceContext()
        );
        ready(userId, quoteSnapshotId, observation);
        when(subscriptionStore.create(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Subscription subscription = service.createFromQuote(
                userId,
                quoteSnapshotId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                createdAt
        ).getSubscription().orElseThrow();

        assertThat(subscription.getCreatedAt()).isEqualTo(createdAt);
        assertThat(subscription.getPriceHistoryStartedAt()).isEqualTo(createdAt);
        assertThat(subscription.getNotificationReferencePrice()).isEmpty();
        assertThat(subscription.getLastProcessedPriceObservedAt()).isEmpty();
        verifyNoInteractions(thresholdNotificationEnqueuer);
    }

    @Test
    void alreadyReachedTargetIsMarkedAndNotificationIsEnqueued() {
        UUID userId = UUID.randomUUID();
        UUID watchTargetId = UUID.randomUUID();
        UUID quoteSnapshotId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        SubscriptionQuoteObservation observation = new SubscriptionQuoteObservation(
                quoteSnapshotId,
                watchTargetId,
                now.minusSeconds(30),
                Optional.of(RubleAmount.ofMinorUnits(8_000)), moscow().toPriceContext()
        );
        ready(userId, quoteSnapshotId, observation);
        when(subscriptionStore.create(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionCreationResult result = service.createFromQuote(
                userId,
                quoteSnapshotId,
                NotificationMode.TARGET_PRICE,
                Optional.of(RubleAmount.ofMinorUnits(8_500)),
                now
        );

        assertThat(result.isCreated()).isTrue();
        assertThat(result.isTargetAlreadyReached()).isTrue();
        assertThat(result.getSubscription().orElseThrow().getWatchTargetId())
                .isEqualTo(watchTargetId);
        verify(thresholdNotificationEnqueuer).enqueue(
                result.getSubscription().orElseThrow(),
                observation,
                now
        );
    }

    @Test
    void futureObservationCannotAuthorizeTrackingCallback() {
        UUID userId = UUID.randomUUID();
        UUID watchTargetId = UUID.randomUUID();
        UUID quoteSnapshotId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        SubscriptionQuoteObservation future = new SubscriptionQuoteObservation(
                quoteSnapshotId,
                watchTargetId,
                now.plusSeconds(1),
                Optional.of(RubleAmount.ofMinorUnits(8_000)), moscow().toPriceContext()
        );
        lockedUser(userId);
        when(subscriptionStore.findQuoteObservation(quoteSnapshotId))
                .thenReturn(Optional.of(future));
        when(subscriptionStore.findActive(userId, watchTargetId)).thenReturn(Optional.empty());

        SubscriptionCreationResult result = service.createFromQuote(
                userId,
                quoteSnapshotId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                now
        );

        assertThat(result.getStatus()).isEqualTo(SubscriptionCreationResult.Status.QUOTE_EXPIRED);
        verify(subscriptionStore, never()).create(any());
    }

    @Test
    void expiredQuoteSnapshotIsCheckedByItsOwnIdentity() {
        UUID userId = UUID.randomUUID();
        UUID watchTargetId = UUID.randomUUID();
        UUID expiredSnapshotId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:20:00Z");
        SubscriptionQuoteObservation expired = new SubscriptionQuoteObservation(
                expiredSnapshotId,
                watchTargetId,
                now.minusSeconds(16 * 60L),
                Optional.of(RubleAmount.ofMinorUnits(8_000)), moscow().toPriceContext()
        );
        lockedUser(userId);
        when(subscriptionStore.findQuoteObservation(expiredSnapshotId))
                .thenReturn(Optional.of(expired));

        SubscriptionCreationResult result = service.createFromQuote(
                userId,
                expiredSnapshotId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                now
        );

        assertThat(result.getStatus()).isEqualTo(SubscriptionCreationResult.Status.QUOTE_EXPIRED);
        verify(subscriptionStore, never()).create(any());
        verify(subscriptionStore, never()).findActive(userId, watchTargetId);
    }

    @Test
    void preventsDuplicateActiveSubscriptionsAndEnforcesTheUserLimit() {
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        UUID existingUserId = UUID.randomUUID();
        UUID existingSnapshotId = UUID.randomUUID();
        SubscriptionQuoteObservation existingObservation = new SubscriptionQuoteObservation(
                existingSnapshotId,
                UUID.randomUUID(),
                now.minusSeconds(30),
                Optional.of(RubleAmount.ofMinorUnits(8_000)), moscow().toPriceContext()
        );
        lockedUser(existingUserId);
        when(subscriptionStore.findQuoteObservation(existingSnapshotId))
                .thenReturn(Optional.of(existingObservation));
        when(subscriptionStore.findActive(
                existingUserId,
                existingObservation.getWatchTargetId()
        )).thenReturn(Optional.of(mock(Subscription.class)));

        SubscriptionCreationResult duplicate = service.createFromQuote(
                existingUserId,
                existingSnapshotId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                now
        );

        UUID limitedUserId = UUID.randomUUID();
        UUID limitedSnapshotId = UUID.randomUUID();
        SubscriptionQuoteObservation limitedObservation = new SubscriptionQuoteObservation(
                limitedSnapshotId,
                UUID.randomUUID(),
                now.minusSeconds(30),
                Optional.of(RubleAmount.ofMinorUnits(8_000)), moscow().toPriceContext()
        );
        lockedUser(limitedUserId);
        when(subscriptionStore.findQuoteObservation(limitedSnapshotId))
                .thenReturn(Optional.of(limitedObservation));
        when(subscriptionStore.findActive(
                limitedUserId,
                limitedObservation.getWatchTargetId()
        )).thenReturn(Optional.empty());
        when(subscriptionStore.countActive(limitedUserId))
                .thenReturn(50L);

        SubscriptionCreationResult limited = service.createFromQuote(
                limitedUserId,
                limitedSnapshotId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                now
        );

        assertThat(duplicate.getStatus())
                .isEqualTo(SubscriptionCreationResult.Status.ALREADY_ACTIVE);
        assertThat(limited.getStatus())
                .isEqualTo(SubscriptionCreationResult.Status.LIMIT_REACHED);
        verify(subscriptionStore, never()).create(any());
    }

    @Test
    void subscriptionLimitBelongsToTheUserProfile() {
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        UUID standardUserId = UUID.randomUUID();
        UUID extendedUserId = UUID.randomUUID();
        SubscriptionQuoteObservation standardObservation = new SubscriptionQuoteObservation(
                UUID.randomUUID(), UUID.randomUUID(), now.minusSeconds(30),
                Optional.of(RubleAmount.ofMinorUnits(8_000)), moscow().toPriceContext()
        );
        SubscriptionQuoteObservation extendedObservation = new SubscriptionQuoteObservation(
                UUID.randomUUID(), UUID.randomUUID(), now.minusSeconds(30),
                Optional.of(RubleAmount.ofMinorUnits(8_000)), moscow().toPriceContext()
        );
        when(userStore.findByIdAndLock(standardUserId)).thenReturn(Optional.of(new UserProfile(
                standardUserId, 1L, 1L, moscow(), UserPricePreferences.defaults(), 10
        )));
        when(userStore.findByIdAndLock(extendedUserId)).thenReturn(Optional.of(new UserProfile(
                extendedUserId, 2L, 2L, moscow(), UserPricePreferences.defaults(), 100
        )));
        when(subscriptionStore.findQuoteObservation(standardObservation.getSnapshotId()))
                .thenReturn(Optional.of(standardObservation));
        when(subscriptionStore.findQuoteObservation(extendedObservation.getSnapshotId()))
                .thenReturn(Optional.of(extendedObservation));
        when(subscriptionStore.findActive(standardUserId, standardObservation.getWatchTargetId()))
                .thenReturn(Optional.empty());
        when(subscriptionStore.findActive(extendedUserId, extendedObservation.getWatchTargetId()))
                .thenReturn(Optional.empty());
        when(subscriptionStore.countActive(standardUserId)).thenReturn(10L);
        when(subscriptionStore.countActive(extendedUserId)).thenReturn(20L);
        when(subscriptionStore.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionCreationResult standard = service.createFromQuote(
                standardUserId, standardObservation.getSnapshotId(),
                NotificationMode.ANY_DECREASE, Optional.empty(), now
        );
        SubscriptionCreationResult extended = service.createFromQuote(
                extendedUserId, extendedObservation.getSnapshotId(),
                NotificationMode.ANY_DECREASE, Optional.empty(), now
        );

        assertThat(standard.getStatus()).isEqualTo(SubscriptionCreationResult.Status.LIMIT_REACHED);
        assertThat(extended.getStatus()).isEqualTo(SubscriptionCreationResult.Status.CREATED);
    }

    private void ready(
            UUID userId,
            UUID quoteSnapshotId,
            SubscriptionQuoteObservation observation
    ) {
        lockedUser(userId);
        when(subscriptionStore.findActive(userId, observation.getWatchTargetId())).thenReturn(Optional.empty());
        when(subscriptionStore.findQuoteObservation(quoteSnapshotId))
                .thenReturn(Optional.of(observation));
        when(subscriptionStore.countActive(userId)).thenReturn(0L);
    }

    @Test
    void oldQuoteFromAnotherRegionCannotCreateAnyOrTargetSubscription() {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        when(userStore.findByIdAndLock(userId)).thenReturn(Optional.of(new UserProfile(
                userId, 1L, 1L, com.priceradar.testsupport.TestMarketplaceRegions.irkutsk(),
                UserPricePreferences.defaults()
        )));
        when(subscriptionStore.findQuoteObservation(snapshotId)).thenReturn(Optional.of(
                new SubscriptionQuoteObservation(
                        snapshotId, UUID.randomUUID(), now.minusSeconds(30),
                        Optional.of(RubleAmount.ofMinorUnits(8_000)), moscow().toPriceContext()
                )
        ));

        SubscriptionCreationResult minimum = service.createFromQuote(
                userId, snapshotId, NotificationMode.ANY_DECREASE, Optional.empty(), now
        );
        SubscriptionCreationResult target = service.createFromQuote(
                userId, snapshotId, NotificationMode.TARGET_PRICE,
                Optional.of(RubleAmount.ofMinorUnits(7_000)), now
        );

        assertThat(minimum.getStatus()).isEqualTo(SubscriptionCreationResult.Status.REGION_MISMATCH);
        assertThat(target.getStatus()).isEqualTo(SubscriptionCreationResult.Status.REGION_MISMATCH);
        verify(subscriptionStore, never()).create(any());
    }

    @Test
    void clearAllRejectsChangedPlanAndEndsTheConfirmedSetAtomically() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        Subscription first = subscription(userId, now, UUID.randomUUID());
        Subscription second = subscription(userId, now, UUID.randomUUID());
        when(userStore.findByIdAndLock(userId)).thenReturn(Optional.of(new UserProfile(
                userId, 1L, 1L, moscow(), UserPricePreferences.defaults()
        )));
        when(subscriptionStore.findActiveSubscriptions(userId))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(first, second));
        when(subscriptionStore.end(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClearSubscriptionsPlan stale = service.prepareClearAll(userId);
        ClearSubscriptionsResult rejected = service.clearAll(userId, stale.getFingerprint(), now);
        ClearSubscriptionsPlan current = service.prepareClearAll(userId);
        ClearSubscriptionsResult cleared = service.clearAll(userId, current.getFingerprint(), now);

        assertThat(rejected.getStatus()).isEqualTo(ClearSubscriptionsResult.Status.PLAN_CHANGED);
        assertThat(cleared.getStatus()).isEqualTo(ClearSubscriptionsResult.Status.CLEARED);
        assertThat(cleared.getEnded()).isEqualTo(2);
        verify(subscriptionStore, org.mockito.Mockito.times(2)).end(any());
    }

    @Test
    void changesAnyDecreaseToTargetInPlaceWithoutResettingIdentityOrTrackingPeriod() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant processedAt = createdAt.plusSeconds(120);
        Subscription current = new Subscription(
                subscriptionId, userId, UUID.randomUUID(), NotificationMode.ANY_DECREASE,
                Optional.empty(), Optional.of(RubleAmount.ofMinorUnits(30_000)),
                Optional.of(processedAt), ThresholdState.NOT_APPLICABLE, Optional.empty(),
                SubscriptionStatus.ACTIVE, createdAt, Optional.empty(), 4
        );
        editable(userId, current, new SubscriptionPriceHistory(
                Optional.of(RubleAmount.ofMinorUnits(30_000)),
                Optional.of(RubleAmount.ofMinorUnits(30_000)),
                Optional.of(processedAt)
        ));

        SubscriptionConditionChangeResult result = service.changeToTargetPrice(
                userId, subscriptionId, RubleAmount.ofMinorUnits(35_000),
                createdAt.plusSeconds(180)
        );

        Subscription changed = result.getSubscription().orElseThrow();
        assertThat(result.getStatus()).isEqualTo(SubscriptionConditionChangeResult.Status.CHANGED);
        assertThat(changed.getId()).isEqualTo(subscriptionId);
        assertThat(changed.getCreatedAt()).isEqualTo(createdAt);
        assertThat(changed.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(changed.getNotificationMode()).isEqualTo(NotificationMode.TARGET_PRICE);
        assertThat(changed.getTargetPrice()).contains(RubleAmount.ofMinorUnits(35_000));
        assertThat(changed.getThresholdState()).isEqualTo(ThresholdState.ABOVE_TARGET);
        assertThat(changed.getThresholdObservedAt()).contains(processedAt);
        assertThat(changed.getLastProcessedPriceObservedAt()).contains(processedAt);
        verify(subscriptionStore, never()).create(any());
        verify(subscriptionStore, never()).end(any());
        verifyNoInteractions(thresholdNotificationEnqueuer);
    }

    @Test
    void changesExistingTargetPriceOnTheSameSubscription() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Subscription current = targetSubscription(
                userId, createdAt, RubleAmount.ofMinorUnits(35_000)
        );
        editable(userId, current, SubscriptionPriceHistory.empty());

        SubscriptionConditionChangeResult result = service.changeToTargetPrice(
                userId, current.getId(), RubleAmount.ofMinorUnits(30_000),
                createdAt.plusSeconds(300)
        );

        assertThat(result.getSubscription().orElseThrow().getId()).isEqualTo(current.getId());
        assertThat(result.getSubscription().orElseThrow().getTargetPrice())
                .contains(RubleAmount.ofMinorUnits(30_000));
        assertThat(result.getSubscription().orElseThrow().getCreatedAt()).isEqualTo(createdAt);
        verify(subscriptionStore, never()).create(any());
    }

    @Test
    void restoresSubscriptionMinimumWhenChangingTargetBackToAnyDecrease() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant latestAt = createdAt.plusSeconds(240);
        Subscription current = targetSubscription(
                userId, createdAt, RubleAmount.ofMinorUnits(28_000)
        );
        editable(userId, current, new SubscriptionPriceHistory(
                Optional.of(RubleAmount.ofMinorUnits(30_000)),
                Optional.of(RubleAmount.ofMinorUnits(36_000)),
                Optional.of(latestAt)
        ));

        SubscriptionConditionChangeResult result = service.changeToAnyDecrease(
                userId, current.getId(), createdAt.plusSeconds(300)
        );

        Subscription changed = result.getSubscription().orElseThrow();
        assertThat(changed.getId()).isEqualTo(current.getId());
        assertThat(changed.getCreatedAt()).isEqualTo(createdAt);
        assertThat(changed.getTargetPrice()).isEmpty();
        assertThat(changed.getNotificationReferencePrice())
                .contains(RubleAmount.ofMinorUnits(30_000));
        assertThat(changed.getLastProcessedPriceObservedAt()).contains(latestAt);

        com.priceradar.notification.application.NotificationDecisionService decisions =
                new com.priceradar.notification.application.NotificationDecisionService();
        var aboveMinimum = decisions.evaluate(
                changed,
                regularObservation(changed, 32_000, latestAt.plusSeconds(60))
        );
        var newMinimum = decisions.evaluate(
                aboveMinimum.getSubscription(),
                regularObservation(changed, 29_000, latestAt.plusSeconds(120))
        );
        assertThat(aboveMinimum.getNotificationIntent()).isEmpty();
        assertThat(newMinimum.getNotificationIntent()).isPresent();
    }

    @Test
    void rejectsMissingEndedOrForeignSubscriptionAndLeavesStateUntouched() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        when(userStore.existsAndLockById(userId)).thenReturn(true);
        when(subscriptionStore.findActiveOwned(userId, subscriptionId)).thenReturn(Optional.empty());

        SubscriptionConditionChangeResult result = service.changeToAnyDecrease(
                userId, subscriptionId, Instant.parse("2026-01-01T00:10:00Z")
        );

        assertThat(result.getStatus()).isEqualTo(SubscriptionConditionChangeResult.Status.NOT_FOUND);
        verify(subscriptionStore, never()).updateConditionIfActive(any());
        verify(subscriptionStore, never()).findValidPriceHistory(any(), any(), any());
    }

    private void lockedUser(UUID userId) {
        when(userStore.findByIdAndLock(userId)).thenReturn(Optional.of(new UserProfile(
                userId, 1L, 1L, moscow(), UserPricePreferences.defaults()
        )));
    }

    private Subscription subscription(UUID userId, Instant now, UUID watchTargetId) {
        return new Subscription(
                UUID.randomUUID(), userId, watchTargetId, NotificationMode.ANY_DECREASE,
                Optional.empty(), Optional.of(RubleAmount.ofMinorUnits(10_000)),
                Optional.of(now.minusSeconds(60)), ThresholdState.NOT_APPLICABLE,
                Optional.empty(), SubscriptionStatus.ACTIVE, now.minusSeconds(120),
                Optional.empty(), 0
        );
    }

    private void editable(
            UUID userId,
            Subscription subscription,
            SubscriptionPriceHistory history
    ) {
        when(userStore.existsAndLockById(userId)).thenReturn(true);
        when(subscriptionStore.findActiveOwned(userId, subscription.getId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionStore.findValidPriceHistory(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(subscription.getId()),
                any()
        )).thenReturn(history);
        when(subscriptionStore.updateConditionIfActive(any()))
                .thenReturn(NotificationStateUpdateResult.UPDATED);
    }

    private Subscription targetSubscription(
            UUID userId,
            Instant createdAt,
            RubleAmount targetPrice
    ) {
        return new Subscription(
                UUID.randomUUID(), userId, UUID.randomUUID(), NotificationMode.TARGET_PRICE,
                Optional.of(targetPrice), Optional.empty(), Optional.of(createdAt.plusSeconds(240)),
                ThresholdState.ABOVE_TARGET, Optional.of(createdAt.plusSeconds(240)),
                SubscriptionStatus.ACTIVE, createdAt, Optional.empty(), 3
        );
    }

    private com.priceradar.notification.application.NotificationObservation regularObservation(
            Subscription subscription,
            long priceMinor,
            Instant observedAt
    ) {
        return new com.priceradar.notification.application.NotificationObservation(
                UUID.randomUUID(),
                subscription.getWatchTargetId(),
                new com.priceradar.pricing.application.InterpretedPrice(
                        Optional.of(RubleAmount.ofMinorUnits(priceMinor)),
                        Optional.empty(),
                        Optional.of(com.priceradar.pricing.domain.PriceSource.PRODUCT),
                        com.priceradar.pricing.domain.SnapshotStatus.REGULAR_PRICE
                ),
                observedAt
        );
    }
}
