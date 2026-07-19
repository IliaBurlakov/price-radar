package com.priceradar.telegram.application;

import java.util.Optional;

public final class TelegramUpdate {

    private final long updateId;
    private final Optional<IncomingTelegramMessage> message;
    private final Optional<IncomingTelegramCallback> callback;

    public TelegramUpdate(long updateId, Optional<IncomingTelegramMessage> message) {
        this(updateId, message, Optional.empty());
    }

    public TelegramUpdate(
            long updateId,
            Optional<IncomingTelegramMessage> message,
            Optional<IncomingTelegramCallback> callback
    ) {
        if (updateId < 0) {
            throw new IllegalArgumentException("updateId must be non-negative");
        }
        if (message == null || callback == null) {
            throw new IllegalArgumentException("update payload must not be null");
        }
        this.updateId = updateId;
        this.message = message;
        this.callback = callback;
    }

    public long getUpdateId() {
        return updateId;
    }

    public Optional<IncomingTelegramMessage> getMessage() {
        return message;
    }

    public Optional<IncomingTelegramCallback> getCallback() {
        return callback;
    }
}
