package com.priceradar.telegram.infrastructure;

public class TelegramGatewayException extends RuntimeException {

    private final boolean retryable;

    public TelegramGatewayException(String message) {
        this(message, false);
    }

    public TelegramGatewayException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
