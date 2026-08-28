package com.priceradar.feedback.application;

import java.time.Instant;
import java.util.UUID;

public final class PendingFeedbackInput {

    private final UUID userId;
    private final long telegramUserId;
    private final long chatId;
    private final Instant createdAt;
    private final Instant expiresAt;

    public PendingFeedbackInput(UUID userId, long telegramUserId, long chatId, Instant createdAt, Instant expiresAt) {
        if (userId == null || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("pending feedback lifecycle is invalid");
        }
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        this.userId = userId;
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() { return userId; }
    public long getTelegramUserId() { return telegramUserId; }
    public long getChatId() { return chatId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
}
