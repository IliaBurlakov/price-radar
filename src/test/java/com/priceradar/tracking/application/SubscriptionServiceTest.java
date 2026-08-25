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
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.priceradar.testsupport.TestMarketplaceRegions.moscow;

class SubscriptionServiceTest {

    private final UserProfileStore userStore = mock(UserProfileStore.class);
    private final SubscriptionStore subscriptionStore = mock(SubscriptionStore.class);
    private final InitialThresholdNotificationEnqueuer thresholdNotificationEnqueuer =
            mock(InitialThresholdNotificationEnqueuer.class);
    private final SubscriptionService service = new SubscriptionService(
            userStore,
            subscriptionStore,
            thresholdNotificationEnqueuer
    );

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
                .thenReturn((long) SubscriptionService.ACTIVE_SUBSCRIPTION_LIMIT);

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
}
