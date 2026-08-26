package com.priceradar.telegram.application;

import java.time.Duration;
import java.util.Optional;

public class TelegramDeliveryException extends RuntimeException {

    private final TelegramDeliveryFailureType failureType;
    private final Optional<Duration> retryAfter;

    public TelegramDeliveryException(
            String message,
            TelegramDeliveryFailureType failureType
    ) {
        this(message, failureType, Optional.empty(), null);
    }

    public TelegramDeliveryException(
            String message,
            TelegramDeliveryFailureType failureType,
            Throwable cause
    ) {
        this(message, failureType, Optional.empty(), cause);
    }

    public TelegramDeliveryException(
            String message,
            TelegramDeliveryFailureType failureType,
            Optional<Duration> retryAfter,
            Throwable cause
    ) {
        super(message, cause);
        if (failureType == null) {
            throw new IllegalArgumentException("failureType must not be null");
        }
        if (retryAfter == null || retryAfter.filter(value -> value.isNegative() || value.isZero()).isPresent()) {
            throw new IllegalArgumentException("retryAfter must be empty or positive");
        }
        this.failureType = failureType;
        this.retryAfter = retryAfter;
    }

    public TelegramDeliveryFailureType getFailureType() {
        return failureType;
    }

    public Optional<Duration> getRetryAfter() {
        return retryAfter;
    }
}
