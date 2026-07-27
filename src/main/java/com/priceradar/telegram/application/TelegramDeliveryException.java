package com.priceradar.telegram.application;

import java.time.Duration;
import java.util.Optional;

public class TelegramDeliveryException extends RuntimeException {

    private final boolean retryable;
    private final Optional<Duration> retryAfter;

    public TelegramDeliveryException(String message, boolean retryable) {
        this(message, retryable, Optional.empty());
    }

    public TelegramDeliveryException(
            String message,
            boolean retryable,
            Optional<Duration> retryAfter
    ) {
        super(message);
        if (retryAfter == null || retryAfter.filter(value -> value.isNegative() || value.isZero()).isPresent()) {
            throw new IllegalArgumentException("retryAfter must be empty or positive");
        }
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Optional<Duration> getRetryAfter() {
        return retryAfter;
    }
}
