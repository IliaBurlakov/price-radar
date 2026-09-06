package com.priceradar.scheduler.application;

import com.priceradar.notification.application.NotificationObservation;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationFanOutJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T08:00:00Z");
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);

    @Test
    void failedFanOutIsRetriedAndThenCompleted() {
        NotificationFanOutJobStore store = mock(NotificationFanOutJobStore.class);
        NotificationFanOutService fanOut = mock(NotificationFanOutService.class);
        NotificationObservation observation = observation();
        PendingNotificationFanOutJob firstAttempt = new PendingNotificationFanOutJob(
                observation,
                0,
                NOW
        );
        PendingNotificationFanOutJob retry = new PendingNotificationFanOutJob(
                observation,
                1,
                NOW.plus(BASE_BACKOFF)
        );
        when(store.findDue(NOW, 10))
                .thenReturn(List.of(firstAttempt), List.of(retry));
        when(store.claim(eq(observation.getSnapshotId()), any(), eq(NOW), any(),
                eq(NOW.plus(CLAIM_TIMEOUT))))
                .thenReturn(true);
        when(store.markRetry(
                eq(observation.getSnapshotId()),
                any(),
                eq(1),
                eq(NOW.plus(BASE_BACKOFF)),
                eq("PROCESSING_FAILED")
        )).thenReturn(true);
        when(store.markDone(eq(observation.getSnapshotId()), any(), eq(NOW)))
                .thenReturn(true);
        doThrow(new IllegalStateException("one subscription failed"))
                .doNothing()
                .when(fanOut).process(observation, NOW);
        NotificationFanOutJobService service = service(store, fanOut);

        service.processDue();
        service.processDue();

        verify(fanOut, times(2)).process(observation, NOW);
        verify(store).markRetry(
                eq(observation.getSnapshotId()),
                any(),
                eq(1),
                eq(NOW.plus(BASE_BACKOFF)),
                eq("PROCESSING_FAILED")
        );
        verify(store).markDone(eq(observation.getSnapshotId()), any(), eq(NOW));
    }

    private NotificationFanOutJobService service(
            NotificationFanOutJobStore store,
            NotificationFanOutService fanOut
    ) {
        return new NotificationFanOutJobService(
                store,
                fanOut,
                Clock.fixed(NOW, ZoneOffset.UTC),
                10,
                CLAIM_TIMEOUT,
                BASE_BACKOFF,
                Duration.ofMinutes(10)
        );
    }

    private NotificationObservation observation() {
        InterpretedPrice price = new InterpretedPrice(
                Optional.of(RubleAmount.ofMinorUnits(40_000)),
                Optional.empty(),
                Optional.of(PriceSource.PRODUCT),
                SnapshotStatus.REGULAR_PRICE
        );
        return new NotificationObservation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                price,
                NOW.minusSeconds(1)
        );
    }
}
