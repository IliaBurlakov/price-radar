package com.priceradar.notification.application;

import com.priceradar.telegram.application.OutgoingTelegramMessage;
import com.priceradar.telegram.application.TelegramDeliveryException;
import com.priceradar.telegram.application.TelegramDeliveryFailureType;
import com.priceradar.telegram.application.TelegramGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class NotificationDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private static final String SUBSCRIPTION_ENDED = "SUBSCRIPTION_ENDED";
    private static final String TELEGRAM_TEMPORARY = "TELEGRAM_TEMPORARY";
    private static final String TELEGRAM_AMBIGUOUS = "TELEGRAM_AMBIGUOUS";
    private static final String TELEGRAM_PERMANENT = "TELEGRAM_PERMANENT";
    private static final String UNEXPECTED_DELIVERY_ERROR = "UNEXPECTED_DELIVERY_ERROR";

    private final NotificationDeliveryStore deliveryStore;
    private final NotificationMessageRenderer messageRenderer;
    private final TelegramGateway telegramGateway;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration claimTimeout;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public NotificationDeliveryService(
            NotificationDeliveryStore deliveryStore,
            NotificationMessageRenderer messageRenderer,
            TelegramGateway telegramGateway,
            Clock clock,
            int batchSize,
            int maxAttempts,
            Duration claimTimeout,
            Duration baseBackoff,
            Duration maxBackoff
    ) {
        if (deliveryStore == null || messageRenderer == null || telegramGateway == null
                || clock == null || claimTimeout == null || baseBackoff == null
                || maxBackoff == null) {
            throw new IllegalArgumentException("notification delivery dependencies must not be null");
        }
        if (batchSize <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("batchSize and maxAttempts must be positive");
        }
        validateDuration(claimTimeout, "claimTimeout");
        validateDuration(baseBackoff, "baseBackoff");
        validateDuration(maxBackoff, "maxBackoff");
        if (baseBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException("baseBackoff must not exceed maxBackoff");
        }
        this.deliveryStore = deliveryStore;
        this.messageRenderer = messageRenderer;
        this.telegramGateway = telegramGateway;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.claimTimeout = claimTimeout;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
    }

    public void deliverDue() {
        Instant batchStartedAt = clock.instant();
        List<PendingNotificationDelivery> notifications = deliveryStore.findDue(
                batchStartedAt,
                batchSize
        );
        for (PendingNotificationDelivery notification : notifications) {
            if (!deliver(notification)) {
                break;
            }
        }
    }

    private boolean deliver(PendingNotificationDelivery notification) {
        Instant claimedAt = clock.instant();
        Instant claimUntil = claimedAt.plus(claimTimeout);
        boolean claimed = deliveryStore.claim(
                notification.getOutboxId(),
                notification.getNextAttemptAt(),
                claimedAt,
                claimUntil
        );
        if (!claimed) {
            return true;
        }

        if (!notification.hasActiveSubscription()) {
            markFailed(notification, claimUntil, SUBSCRIPTION_ENDED);
            return true;
        }

        OutgoingTelegramMessage message;
        try {
            message = messageRenderer.render(notification);
        } catch (RuntimeException exception) {
            retryOrFail(
                    notification,
                    claimUntil,
                    UNEXPECTED_DELIVERY_ERROR,
                    Optional.empty(),
                    exception
            );
            return true;
        }

        try {
            telegramGateway.sendMessage(message);
        } catch (TelegramDeliveryException exception) {
            if (exception.getFailureType() == TelegramDeliveryFailureType.SAFE_TO_RETRY) {
                retryOrFail(
                        notification,
                        claimUntil,
                        TELEGRAM_TEMPORARY,
                        exception.getRetryAfter(),
                        exception
                );
                return false;
            }
            if (exception.getFailureType() == TelegramDeliveryFailureType.DELIVERY_AMBIGUOUS) {
                retryOrFail(
                        notification,
                        claimUntil,
                        TELEGRAM_AMBIGUOUS,
                        Optional.empty(),
                        exception
                );
                return false;
            }
            LOGGER.warn(
                    "Telegram notification delivery failed permanently, outboxId={}, attempt={}, failureType={}, causeType={}, error={}",
                    notification.getOutboxId(),
                    notification.getAttemptCount() + 1,
                    exception.getFailureType(),
                    rootCauseType(exception),
                    exception.getMessage()
            );
            markFailed(notification, claimUntil, TELEGRAM_PERMANENT);
            return true;
        } catch (RuntimeException exception) {
            retryOrFail(
                    notification,
                    claimUntil,
                    UNEXPECTED_DELIVERY_ERROR,
                    Optional.empty(),
                    exception
            );
            return true;
        }

        boolean sent = deliveryStore.markSent(
                notification.getOutboxId(),
                claimUntil,
                clock.instant()
        );
        if (!sent) {
            LOGGER.warn(
                    "Notification was sent but its outbox claim changed, outboxId={}",
                    notification.getOutboxId()
            );
        } else {
            LOGGER.info(
                    "Telegram notification delivered, outboxId={}, attemptsBeforeSuccess={}",
                    notification.getOutboxId(),
                    notification.getAttemptCount()
            );
        }
        return true;
    }

    private void retryOrFail(
            PendingNotificationDelivery notification,
            Instant claimUntil,
            String errorCode,
            Optional<Duration> requestedDelay,
            RuntimeException exception
    ) {
        int attemptCount = notification.getAttemptCount() + 1;
        if (attemptCount >= maxAttempts) {
            boolean failed = deliveryStore.markFailed(
                    notification.getOutboxId(),
                    claimUntil,
                    attemptCount,
                    errorCode
            );
            logStateConflict(failed, notification.getOutboxId(), "FAILED");
            if (failed) {
                LOGGER.warn(
                        "Notification delivery attempts exhausted, outboxId={}, attempt={}, failureType={}, causeType={}, status=FAILED",
                        notification.getOutboxId(),
                        attemptCount,
                        failureType(exception),
                        rootCauseType(exception)
                );
            }
            return;
        }
        Duration calculatedBackoff = backoff(attemptCount);
        Duration retryDelay = requestedDelay
                .filter(value -> value.compareTo(calculatedBackoff) > 0)
                .orElse(calculatedBackoff);
        Instant nextAttemptAt = clock.instant().plus(retryDelay);
        boolean retried = deliveryStore.markRetry(
                notification.getOutboxId(),
                claimUntil,
                attemptCount,
                nextAttemptAt,
                errorCode
        );
        logStateConflict(retried, notification.getOutboxId(), "RETRY");
        if (retried) {
            LOGGER.warn(
                    "Notification delivery retry scheduled, outboxId={}, attempt={}, failureType={}, causeType={}, nextAttemptAt={}",
                    notification.getOutboxId(),
                    attemptCount,
                    failureType(exception),
                    rootCauseType(exception),
                    nextAttemptAt
            );
        }
    }

    private void markFailed(
            PendingNotificationDelivery notification,
            Instant claimUntil,
            String errorCode
    ) {
        boolean failed = deliveryStore.markFailed(
                notification.getOutboxId(),
                claimUntil,
                notification.getAttemptCount() + 1,
                errorCode
        );
        logStateConflict(failed, notification.getOutboxId(), "FAILED");
    }

    private void logStateConflict(boolean updated, UUID outboxId, String targetState) {
        if (!updated) {
            LOGGER.warn(
                    "Notification outbox claim changed before transition, outboxId={}, targetState={}",
                    outboxId,
                    targetState
            );
        }
    }

    private Duration backoff(int attemptCount) {
        int exponent = Math.min(attemptCount - 1, 30);
        long multiplier = 1L << exponent;
        Duration calculated;
        try {
            calculated = baseBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return maxBackoff;
        }
        return calculated.compareTo(maxBackoff) > 0 ? maxBackoff : calculated;
    }

    private String rootCauseType(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName();
    }

    private Object failureType(RuntimeException exception) {
        if (exception instanceof TelegramDeliveryException telegramException) {
            return telegramException.getFailureType();
        }
        return UNEXPECTED_DELIVERY_ERROR;
    }

    private void validateDuration(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
