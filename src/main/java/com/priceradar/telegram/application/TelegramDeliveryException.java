package com.priceradar.telegram.application;

public class TelegramDeliveryException extends RuntimeException {

    private final boolean retryable;

    public TelegramDeliveryException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
