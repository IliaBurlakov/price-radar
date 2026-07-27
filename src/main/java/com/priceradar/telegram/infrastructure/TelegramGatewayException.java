package com.priceradar.telegram.infrastructure;

import com.priceradar.telegram.application.TelegramDeliveryException;

import java.time.Duration;
import java.util.Optional;

public class TelegramGatewayException extends TelegramDeliveryException {

    public TelegramGatewayException(String message) {
        this(message, false);
    }

    public TelegramGatewayException(String message, boolean retryable) {
        super(message, retryable);
    }

    public TelegramGatewayException(
            String message,
            boolean retryable,
            Optional<Duration> retryAfter
    ) {
        super(message, retryable, retryAfter);
    }
}
