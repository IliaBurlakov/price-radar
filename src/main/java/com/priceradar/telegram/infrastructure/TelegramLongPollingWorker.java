package com.priceradar.telegram.infrastructure;

import com.priceradar.telegram.application.TelegramGateway;
import com.priceradar.telegram.application.TelegramPollingStateStore;
import com.priceradar.telegram.application.TelegramUpdate;
import com.priceradar.telegram.application.TelegramUpdateDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

public class TelegramLongPollingWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramLongPollingWorker.class);

    private final TelegramGateway telegramGateway;
    private final TelegramUpdateDispatcher updateDispatcher;
    private final TelegramPollingStateStore pollingStateStore;
    private final String botKey;
    private final Duration longPollingTimeout;
    private final int maxUpdateAttempts;

    public TelegramLongPollingWorker(
            TelegramGateway telegramGateway,
            TelegramUpdateDispatcher updateDispatcher,
            TelegramPollingStateStore pollingStateStore,
            String botKey,
            Duration longPollingTimeout,
            int maxUpdateAttempts
    ) {
        if (telegramGateway == null || updateDispatcher == null || pollingStateStore == null
                || botKey == null || botKey.isBlank() || longPollingTimeout == null) {
            throw new IllegalArgumentException("Telegram polling worker fields must not be null or blank");
        }
        if (longPollingTimeout.isZero() || longPollingTimeout.isNegative()) {
            throw new IllegalArgumentException("longPollingTimeout must be positive");
        }
        if (maxUpdateAttempts <= 0) {
            throw new IllegalArgumentException("maxUpdateAttempts must be positive");
        }
        this.telegramGateway = telegramGateway;
        this.updateDispatcher = updateDispatcher;
        this.pollingStateStore = pollingStateStore;
        this.botKey = botKey.trim();
        this.longPollingTimeout = longPollingTimeout;
        this.maxUpdateAttempts = maxUpdateAttempts;
    }

    @Scheduled(
            initialDelayString = "${priceradar.telegram.initial-delay:PT1S}",
            fixedDelayString = "${priceradar.telegram.poll-delay:PT1S}"
    )
    public void poll() {
        try {
            long offset = nextOffset(pollingStateStore.findLastConfirmedUpdateId(botKey));
            List<TelegramUpdate> updates = telegramGateway.receiveUpdates(offset, longPollingTimeout);
            for (TelegramUpdate update : updates) {
                if (!process(update)) {
                    break;
                }
            }
        } catch (TelegramGatewayException exception) {
            LOGGER.warn("Telegram polling failed: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Telegram update processing failed; update will be retried, errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private boolean process(TelegramUpdate update) {
        try {
            updateDispatcher.dispatch(update);
        } catch (TelegramGatewayException exception) {
            if (exception.isRetryable()) {
                LOGGER.warn(
                        "Telegram update delivery failed transiently; update will be retried, updateId={}, error={}",
                        update.getUpdateId(),
                        exception.getMessage()
                );
                return false;
            }
            return handlePermanentFailure(update, exception);
        } catch (RuntimeException exception) {
            return handlePermanentFailure(update, exception);
        }

        pollingStateStore.confirm(botKey, update.getUpdateId());
        return true;
    }

    private boolean handlePermanentFailure(
            TelegramUpdate update,
            RuntimeException exception
    ) {
        int attempts = pollingStateStore.recordFailure(botKey, update.getUpdateId());
        if (attempts < maxUpdateAttempts) {
            LOGGER.warn(
                    "Telegram update processing failed; update will be retried, updateId={}, attempt={}, errorType={}",
                    update.getUpdateId(),
                    attempts,
                    exception.getClass().getSimpleName()
            );
            return false;
        }
        LOGGER.error(
                "Telegram update moved past after repeated processing failures, updateId={}, attempts={}, errorType={}",
                update.getUpdateId(),
                attempts,
                exception.getClass().getSimpleName()
        );
        pollingStateStore.confirm(botKey, update.getUpdateId());
        return true;
    }

    private long nextOffset(OptionalLong lastConfirmedUpdateId) {
        if (lastConfirmedUpdateId.isEmpty()) {
            return 0;
        }
        return Math.addExact(lastConfirmedUpdateId.getAsLong(), 1);
    }
}
