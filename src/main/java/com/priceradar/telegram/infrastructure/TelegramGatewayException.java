package com.priceradar.telegram.infrastructure;

public class TelegramGatewayException extends RuntimeException {

    public TelegramGatewayException(String message) {
        super(message);
    }
}
