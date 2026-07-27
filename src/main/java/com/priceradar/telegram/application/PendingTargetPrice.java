package com.priceradar.telegram.application;

import java.time.Instant;
import java.util.UUID;

public final class PendingTargetPrice {

    private final long telegramUserId;
    private final long chatId;
    private final UUID quoteSnapshotId;
    private final Instant expiresAt;

    public PendingTargetPrice(
            long telegramUserId,
            long chatId,
            UUID quoteSnapshotId,
            Instant expiresAt
    ) {
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        if (quoteSnapshotId == null || expiresAt == null) {
            throw new IllegalArgumentException("pending target price fields must not be null");
        }
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.quoteSnapshotId = quoteSnapshotId;
        this.expiresAt = expiresAt;
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public long getChatId() {
        return chatId;
    }

    public UUID getQuoteSnapshotId() {
        return quoteSnapshotId;
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
