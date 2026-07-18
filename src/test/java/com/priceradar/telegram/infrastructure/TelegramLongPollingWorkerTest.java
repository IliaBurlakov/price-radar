package com.priceradar.telegram.infrastructure;

import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.TelegramPollingStateStore;
import com.priceradar.telegram.application.TelegramUpdate;
import com.priceradar.telegram.application.TelegramUpdateDispatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramLongPollingWorkerTest {

    @Test
    void poisonUpdateDoesNotBlockLaterUpdatesForever() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramUpdateDispatcher dispatcher = mock(TelegramUpdateDispatcher.class);
        InMemoryPollingStateStore stateStore = new InMemoryPollingStateStore();
        TelegramUpdate poison = new TelegramUpdate(10L, Optional.empty());
        TelegramUpdate next = new TelegramUpdate(11L, Optional.empty());
        when(gateway.receiveUpdates(0L, Duration.ofSeconds(1)))
                .thenReturn(List.of(poison, next));
        doThrow(new IllegalStateException("permanent failure"))
                .when(dispatcher).dispatch(poison);
        TelegramLongPollingWorker worker = new TelegramLongPollingWorker(
                gateway,
                dispatcher,
                stateStore,
                "test",
                Duration.ofSeconds(1),
                2
        );

        worker.poll();
        assertThat(stateStore.findLastConfirmedUpdateId("test")).isEmpty();

        worker.poll();

        assertThat(stateStore.findLastConfirmedUpdateId("test")).hasValue(11L);
        verify(dispatcher).dispatch(next);
    }

    @Test
    void transientTelegramFailureIsNeverSkippedByPoisonLimit() {
        TelegramGateway gateway = mock(TelegramGateway.class);
        TelegramUpdateDispatcher dispatcher = mock(TelegramUpdateDispatcher.class);
        InMemoryPollingStateStore stateStore = new InMemoryPollingStateStore();
        TelegramUpdate update = new TelegramUpdate(20L, Optional.empty());
        when(gateway.receiveUpdates(0L, Duration.ofSeconds(1)))
                .thenReturn(List.of(update));
        doThrow(new TelegramGatewayException("temporary outage", true))
                .when(dispatcher).dispatch(update);
        TelegramLongPollingWorker worker = new TelegramLongPollingWorker(
                gateway,
                dispatcher,
                stateStore,
                "test",
                Duration.ofSeconds(1),
                1
        );

        worker.poll();
        worker.poll();

        assertThat(stateStore.findLastConfirmedUpdateId("test")).isEmpty();
        assertThat(stateStore.attempts).isZero();
    }

    private static final class InMemoryPollingStateStore
            implements TelegramPollingStateStore {

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
