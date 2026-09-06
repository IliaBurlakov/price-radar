package com.priceradar.scheduler.application;

import com.priceradar.notification.application.NotificationObservation;
import com.priceradar.notification.application.NotificationDecisionService;
import com.priceradar.notification.application.NotificationIntent;
import com.priceradar.notification.application.NotificationOutboxStore;
import com.priceradar.notification.application.NotificationOutboxWriter;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.NotificationStateUpdateResult;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationFanOutServiceTest {

    @Test
    void oneBrokenSubscriptionDoesNotPreventProcessingTheOthers() {
        SubscriptionStore store = mock(SubscriptionStore.class);
        SubscriptionNotificationProcessor processor = mock(SubscriptionNotificationProcessor.class);
        NotificationObservation observation = mock(NotificationObservation.class);
        UUID watchTargetId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-07-19T08:00:00Z");
        when(observation.getWatchTargetId()).thenReturn(watchTargetId);
        when(observation.getSnapshotId()).thenReturn(UUID.randomUUID());
        when(store.findActiveIdsByWatchTargetId(watchTargetId))
                .thenReturn(List.of(firstId, secondId));
        doThrow(new IllegalStateException("broken subscription"))
                .when(processor).process(firstId, observation, completedAt);
        NotificationFanOutService service = new NotificationFanOutService(store, processor);

        assertThatThrownBy(() -> service.process(observation, completedAt))
                .isInstanceOf(IllegalStateException.class);

        verify(processor).process(secondId, observation, completedAt);
    }

    @Test
    void retryingPartialFanOutDoesNotCreateDuplicateNotifications() {
        Instant createdAt = Instant.parse("2026-09-05T07:00:00Z");
        Instant observedAt = createdAt.plusSeconds(60);
        Instant completedAt = observedAt.plusSeconds(1);
        UUID watchTargetId = UUID.randomUUID();
        Subscription first = subscription(watchTargetId, createdAt);
        Subscription second = subscription(watchTargetId, createdAt);
        Map<UUID, Subscription> states = new HashMap<>();
        states.put(first.getId(), first);
        states.put(second.getId(), second);

        SubscriptionStore subscriptionStore = mock(SubscriptionStore.class);
        when(subscriptionStore.findActiveIdsByWatchTargetId(watchTargetId))
                .thenReturn(List.of(first.getId(), second.getId()));
        when(subscriptionStore.findActiveById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(states.get(invocation.getArgument(0))));
        AtomicBoolean failSecondOnce = new AtomicBoolean(true);
        when(subscriptionStore.updateNotificationStateIfActive(any()))
                .thenAnswer(invocation -> {
                    Subscription updated = invocation.getArgument(0);
                    if (updated.getId().equals(second.getId()) && failSecondOnce.getAndSet(false)) {
                        throw new IllegalStateException("temporary subscription failure");
                    }
                    states.put(updated.getId(), updated);
                    return NotificationStateUpdateResult.UPDATED;
                });

        Set<String> idempotencyKeys = new HashSet<>();
        Map<UUID, NotificationIntent> notifications = new HashMap<>();
        NotificationOutboxStore outboxStore = mock(NotificationOutboxStore.class);
        when(outboxStore.enqueueIfAbsent(any(), any(), any()))
                .thenAnswer(invocation -> {
                    NotificationIntent intent = invocation.getArgument(0);
                    String key = invocation.getArgument(1);
                    boolean inserted = idempotencyKeys.add(key);
                    if (inserted) {
                        notifications.put(intent.getSubscriptionId(), intent);
                    }
                    return inserted;
                });
        SubscriptionNotificationProcessor processor = new SubscriptionNotificationProcessor(
                subscriptionStore,
                new NotificationDecisionService(),
                new NotificationOutboxWriter(subscriptionStore, outboxStore)
        );
        NotificationFanOutService fanOut = new NotificationFanOutService(
                subscriptionStore,
                processor
        );
        NotificationObservation observation = new NotificationObservation(
                UUID.randomUUID(),
                watchTargetId,
                regularPrice(40_000),
                observedAt
        );

        assertThatThrownBy(() -> fanOut.process(observation, completedAt))
                .isInstanceOf(IllegalStateException.class);
        fanOut.process(observation, completedAt);

        assertThat(notifications).containsOnlyKeys(first.getId(), second.getId());
        assertThat(idempotencyKeys).hasSize(2);
    }

    private Subscription subscription(UUID watchTargetId, Instant createdAt) {
        return new Subscription(
                UUID.randomUUID(),
                UUID.randomUUID(),
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                Optional.of(RubleAmount.ofMinorUnits(50_000)),
                Optional.of(createdAt),
                ThresholdState.NOT_APPLICABLE,
                Optional.empty(),
                SubscriptionStatus.ACTIVE,
                createdAt,
                Optional.empty(),
                0
        );
    }

    private InterpretedPrice regularPrice(long minorUnits) {
        return new InterpretedPrice(
                Optional.of(RubleAmount.ofMinorUnits(minorUnits)),
                Optional.empty(),
                Optional.of(PriceSource.PRODUCT),
                SnapshotStatus.REGULAR_PRICE
        );
    }
}
