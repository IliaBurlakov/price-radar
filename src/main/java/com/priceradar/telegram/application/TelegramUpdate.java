package com.priceradar.telegram.application;

import java.util.Optional;

public final class TelegramUpdate {

    private final long updateId;
    private final Optional<IncomingTelegramMessage> message;

    public TelegramUpdate(long updateId, Optional<IncomingTelegramMessage> message) {
        if (updateId < 0) {
            throw new IllegalArgumentException("updateId must be non-negative");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        this.updateId = updateId;
        this.message = message;
    }

    public long getUpdateId() {
        return updateId;
    }

    public Optional<IncomingTelegramMessage> getMessage() {
        return message;
    }
}
