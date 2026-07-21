package com.priceradar.notification.application;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationOutboxWriterTest {

    @Test
    void stalePersistenceDecisionCannotCreateOutboxEvent() {
        SubscriptionStore subscriptionStore = mock(SubscriptionStore.class);
        NotificationOutboxStore outboxStore = mock(NotificationOutboxStore.class);
        NotificationOutboxWriter writer = new NotificationOutboxWriter(
                subscriptionStore,
                outboxStore
        );
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        UUID watchTargetId = UUID.randomUUID();
        Subscription subscription = new Subscription(
                UUID.randomUUID(),
                UUID.randomUUID(),
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                Optional.of(RubleAmount.ofMinorUnits(9_000)),
                Optional.of(createdAt),
                ThresholdState.NOT_APPLICABLE,
                Optional.empty(),
                SubscriptionStatus.ACTIVE,
                createdAt,
                Optional.empty(),
                3
        );
        NotificationDecisionResult decision = new NotificationDecisionService().evaluate(
                subscription,
                new NotificationObservation(
                        UUID.randomUUID(),
                        watchTargetId,
                        regularPrice(8_000),
                        createdAt.plusSeconds(60)
                )
        );
        when(subscriptionStore.updateNotificationStateIfActive(any()))
                .thenReturn(NotificationStateUpdateResult.CONFLICT);

        NotificationOutboxWriteResult result = writer.persist(
                decision,
                createdAt.plusSeconds(61)
        );

        assertThat(result).isEqualTo(NotificationOutboxWriteResult.STATE_CONFLICT);
        verify(outboxStore, never()).enqueueIfAbsent(any(), any(), any());
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
