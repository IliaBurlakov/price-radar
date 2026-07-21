package com.priceradar.telegram.application;

import java.time.Instant;
import java.util.UUID;

public final class PendingTargetPrice {

    private final long telegramUserId;
    private final long chatId;
    private final UUID watchTargetId;
    private final Instant expiresAt;

    public PendingTargetPrice(
            long telegramUserId,
            long chatId,
            UUID watchTargetId,
            Instant expiresAt
    ) {
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        if (watchTargetId == null || expiresAt == null) {
            throw new IllegalArgumentException("pending target price fields must not be null");
        }
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.watchTargetId = watchTargetId;
        this.expiresAt = expiresAt;
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public long getChatId() {
        return chatId;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        return now.isAfter(expiresAt);
    }
}
