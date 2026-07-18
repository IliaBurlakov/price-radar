package com.priceradar.tracking.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.user.application.UserProfileStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {

    private final UserProfileStore userStore = mock(UserProfileStore.class);
    private final SubscriptionStore subscriptionStore = mock(SubscriptionStore.class);
    private final ImmediateThresholdNotificationPort immediatePort =
            mock(ImmediateThresholdNotificationPort.class);
    private final SubscriptionService service = new SubscriptionService(
            userStore,
            subscriptionStore,
            immediatePort
    );

    @Test
    void alreadyReachedTargetCreatesSubscriptionAndImmediateEvent() {
        UUID userId = UUID.randomUUID();
        UUID watchTargetId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        SubscriptionQuoteObservation observation = new SubscriptionQuoteObservation(
                UUID.randomUUID(),
                now.minusSeconds(30),
                Optional.of(RubleAmount.ofMinorUnits(8_000))
        );
        ready(userId, watchTargetId, observation);
        when(subscriptionStore.create(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionCreationResult result = service.createFromQuote(
                userId,
                watchTargetId,
                NotificationMode.TARGET_PRICE,
                Optional.of(RubleAmount.ofMinorUnits(8_500)),
                now
        );

        assertThat(result.isCreated()).isTrue();
        assertThat(result.isTargetAlreadyReached()).isTrue();
        verify(immediatePort).enqueue(
                result.getSubscription().orElseThrow(),
                observation,
                now
        );
    }

    @Test
    void futureObservationCannotAuthorizeTrackingCallback() {
        UUID userId = UUID.randomUUID();
        UUID watchTargetId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        SubscriptionQuoteObservation future = new SubscriptionQuoteObservation(
                UUID.randomUUID(),
                now.plusSeconds(1),
                Optional.of(RubleAmount.ofMinorUnits(8_000))
        );
        when(userStore.existsAndLockById(userId)).thenReturn(true);
        when(subscriptionStore.findActive(userId, watchTargetId)).thenReturn(Optional.empty());
        when(subscriptionStore.watchTargetExists(watchTargetId)).thenReturn(true);
        when(subscriptionStore.findLatestQuoteObservation(watchTargetId))
                .thenReturn(Optional.of(future));

        SubscriptionCreationResult result = service.createFromQuote(
                userId,
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                now
        );

        assertThat(result.getStatus()).isEqualTo(SubscriptionCreationResult.Status.QUOTE_EXPIRED);
        verify(subscriptionStore, never()).create(any());
        verify(immediatePort, never()).enqueue(any(), any(), any());
    }

    private void ready(
            UUID userId,
            UUID watchTargetId,
            SubscriptionQuoteObservation observation
    ) {
        when(userStore.existsAndLockById(userId)).thenReturn(true);
        when(subscriptionStore.findActive(userId, watchTargetId)).thenReturn(Optional.empty());
        when(subscriptionStore.watchTargetExists(watchTargetId)).thenReturn(true);
        when(subscriptionStore.findLatestQuoteObservation(watchTargetId))
                .thenReturn(Optional.of(observation));
        when(subscriptionStore.findLatestRegularPriceObservation(watchTargetId))
                .thenReturn(Optional.of(observation));
        when(subscriptionStore.countActive(userId)).thenReturn(0L);
    }
}
