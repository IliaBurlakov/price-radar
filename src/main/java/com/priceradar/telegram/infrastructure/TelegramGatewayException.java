package com.priceradar.telegram.infrastructure;

import com.priceradar.telegram.application.TelegramDeliveryException;

public class TelegramGatewayException extends TelegramDeliveryException {

    public TelegramGatewayException(String message) {
        this(message, false);
    }

    public TelegramGatewayException(String message, boolean retryable) {
        super(message, retryable);
    }
}
