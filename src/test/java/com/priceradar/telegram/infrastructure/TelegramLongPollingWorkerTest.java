package com.priceradar.telegram.infrastructure;

import com.priceradar.telegram.application.TelegramDeliveryException;
import com.priceradar.telegram.application.TelegramDeliveryFailureType;
import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.TelegramPollingStateStore;
import com.priceradar.telegram.application.TelegramUpdate;
import com.priceradar.telegram.application.TelegramUpdateDispatcher;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramLongPollingWorkerTest {

    private static final Duration POLLING_TIMEOUT = Duration.ofSeconds(1);

    @Test
    void poisonUpdateDoesNotBlockLaterUpdatesForever() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramUpdateDispatcher dispatcher = mock(TelegramUpdateDispatcher.class);
        InMemoryPollingStateStore stateStore = new InMemoryPollingStateStore();
        TelegramUpdate poison = new TelegramUpdate(10L, Optional.empty());
        TelegramUpdate next = new TelegramUpdate(11L, Optional.empty());
        when(gateway.receiveUpdates(0L, POLLING_TIMEOUT))
                .thenReturn(List.of(poison, next));
        doThrow(new IllegalStateException("permanent failure"))
                .when(dispatcher).dispatch(poison);
        TelegramLongPollingWorker worker = worker(gateway, dispatcher, stateStore, new MutableClock());

        worker.poll();
        assertThat(stateStore.findLastConfirmedUpdateId("test")).isEmpty();

        worker.poll();

        assertThat(stateStore.findLastConfirmedUpdateId("test")).hasValue(11L);
        verify(dispatcher).dispatch(next);
    }

    @Test
    void pollingBackoffGrowsAndPreventsTightRetries() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        MutableClock clock = new MutableClock();
        when(gateway.receiveUpdates(0L, POLLING_TIMEOUT)).thenThrow(safeFailure());
        TelegramLongPollingWorker worker = worker(
                gateway,
                mock(TelegramUpdateDispatcher.class),
                new InMemoryPollingStateStore(),
                clock
        );

        worker.poll();
        worker.poll();
        verify(gateway).receiveUpdates(0L, POLLING_TIMEOUT);

        clock.advance(Duration.ofSeconds(5));
        worker.poll();
        worker.poll();
        verify(gateway, times(2)).receiveUpdates(0L, POLLING_TIMEOUT);

        clock.advance(Duration.ofSeconds(9));
        worker.poll();
        verify(gateway, times(2)).receiveUpdates(0L, POLLING_TIMEOUT);

        clock.advance(Duration.ofSeconds(1));
        worker.poll();
        verify(gateway, times(3)).receiveUpdates(0L, POLLING_TIMEOUT);

        clock.advance(Duration.ofSeconds(20));
        worker.poll();
        clock.advance(Duration.ofSeconds(40));
        worker.poll();
        clock.advance(Duration.ofSeconds(59));
        worker.poll();
        verify(gateway, times(5)).receiveUpdates(0L, POLLING_TIMEOUT);

        clock.advance(Duration.ofSeconds(1));
        worker.poll();
        verify(gateway, times(6)).receiveUpdates(0L, POLLING_TIMEOUT);

        clock.advance(Duration.ofSeconds(60));
        worker.poll();
        verify(gateway, times(7)).receiveUpdates(0L, POLLING_TIMEOUT);
    }

    @Test
    void successfulPollResetsBackoff() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        MutableClock clock = new MutableClock();
        when(gateway.receiveUpdates(0L, POLLING_TIMEOUT))
                .thenThrow(safeFailure())
                .thenReturn(List.of())
                .thenThrow(safeFailure());
        TelegramLongPollingWorker worker = worker(
                gateway,
                mock(TelegramUpdateDispatcher.class),
                new InMemoryPollingStateStore(),
                clock
        );

        worker.poll();
        clock.advance(Duration.ofSeconds(5));
        worker.poll();
        worker.poll();
        clock.advance(Duration.ofSeconds(4));
        worker.poll();

        verify(gateway, times(3)).receiveUpdates(0L, POLLING_TIMEOUT);
        clock.advance(Duration.ofSeconds(1));
        worker.poll();
        verify(gateway, times(4)).receiveUpdates(0L, POLLING_TIMEOUT);
    }

    @Test
    void telegramRetryAfterTakesPriorityOverLocalBackoff() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        MutableClock clock = new MutableClock();
        TelegramDeliveryException rateLimit = new TelegramDeliveryException(
                "rate limited",
                TelegramDeliveryFailureType.SAFE_TO_RETRY,
                Optional.of(Duration.ofSeconds(30)),
                null
        );
        when(gateway.receiveUpdates(0L, POLLING_TIMEOUT))
                .thenThrow(rateLimit)
                .thenReturn(List.of());
        TelegramLongPollingWorker worker = worker(
                gateway,
                mock(TelegramUpdateDispatcher.class),
                new InMemoryPollingStateStore(),
                clock
        );

        worker.poll();
        clock.advance(Duration.ofSeconds(29));
        worker.poll();
        verify(gateway).receiveUpdates(0L, POLLING_TIMEOUT);

        clock.advance(Duration.ofSeconds(1));
        worker.poll();
        verify(gateway, times(2)).receiveUpdates(0L, POLLING_TIMEOUT);
    }

    @Test
    void ambiguousUpdateResponseIsRetriedWithBackoffUpToUpdateLimit() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramUpdateDispatcher dispatcher = mock(TelegramUpdateDispatcher.class);
        InMemoryPollingStateStore stateStore = new InMemoryPollingStateStore();
        TelegramUpdate update = new TelegramUpdate(20L, Optional.empty());
        when(gateway.receiveUpdates(0L, POLLING_TIMEOUT)).thenReturn(List.of(update));
        doThrow(new TelegramDeliveryException(
                "response timeout",
                TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS,
                new java.net.http.HttpTimeoutException("response timeout")
        )).when(dispatcher).dispatch(update);
        MutableClock clock = new MutableClock();
        TelegramLongPollingWorker worker = worker(gateway, dispatcher, stateStore, clock);

        worker.poll();
        worker.poll();
        assertThat(stateStore.findLastConfirmedUpdateId("test")).isEmpty();
        verify(dispatcher).dispatch(update);

        clock.advance(Duration.ofSeconds(5));
        worker.poll();

        assertThat(stateStore.findLastConfirmedUpdateId("test")).hasValue(20L);
        verify(dispatcher, times(2)).dispatch(update);
    }

    private TelegramLongPollingWorker worker(
            TelegramGateway gateway,
            TelegramUpdateDispatcher dispatcher,
            TelegramPollingStateStore stateStore,
            Clock clock
    ) {
        return new TelegramLongPollingWorker(
                gateway,
                dispatcher,
                stateStore,
                "test",
                POLLING_TIMEOUT,
                2,
                clock
        );
    }

    private TelegramDeliveryException safeFailure() {
        return new TelegramDeliveryException(
                "connection failed",
                TelegramDeliveryFailureType.SAFE_TO_RETRY,
                new ConnectException("connection refused")
        );
    }

    private static final class MutableClock extends Clock {

        private Instant current = Instant.parse("2026-08-26T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }
    }

    private static final class InMemoryPollingStateStore implements TelegramPollingStateStore {

        private long lastConfirmed = -1;
        private long failedUpdate = -1;
        private int attempts;

        @Override
        public OptionalLong findLastConfirmedUpdateId(String botKey) {
            return lastConfirmed < 0
                    ? OptionalLong.empty()
                    : OptionalLong.of(lastConfirmed);
        }

        @Override
        public void confirm(String botKey, long updateId) {
            lastConfirmed = Math.max(lastConfirmed, updateId);
            failedUpdate = -1;
            attempts = 0;
        }

        @Override
        public int recordFailure(String botKey, long updateId) {
            if (failedUpdate == updateId) {
                attempts++;
            } else {
                failedUpdate = updateId;
                attempts = 1;
            }
            return attempts;
        }
    }
}
