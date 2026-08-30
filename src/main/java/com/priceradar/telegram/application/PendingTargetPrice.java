package com.priceradar.telegram.application;

import java.time.Instant;
import java.util.UUID;

public final class PendingTargetPrice {

    public enum Purpose {
        CREATE_SUBSCRIPTION,
        EDIT_SUBSCRIPTION
    }

    private final long telegramUserId;
    private final long chatId;
    private final Purpose purpose;
    private final UUID referenceId;
    private final Instant expiresAt;

    public PendingTargetPrice(
            long telegramUserId,
            long chatId,
            Purpose purpose,
            UUID referenceId,
            Instant expiresAt
    ) {
        if (telegramUserId <= 0 || chatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        if (purpose == null || referenceId == null || expiresAt == null) {
            throw new IllegalArgumentException("pending target price fields must not be null");
        }
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.purpose = purpose;
        this.referenceId = referenceId;
        this.expiresAt = expiresAt;
    }

    public PendingTargetPrice(
            long telegramUserId,
            long chatId,
            UUID quoteSnapshotId,
            Instant expiresAt
    ) {
        this(
                telegramUserId,
                chatId,
                Purpose.CREATE_SUBSCRIPTION,
                quoteSnapshotId,
                expiresAt
        );
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public long getChatId() {
        return chatId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public UUID requireQuoteSnapshotId() {
        if (purpose != Purpose.CREATE_SUBSCRIPTION) {
            throw new IllegalStateException("pending input does not create a subscription");
        }
        return referenceId;
    }

    public UUID getQuoteSnapshotId() {
        return requireQuoteSnapshotId();
    }

    public UUID requireSubscriptionId() {
        if (purpose != Purpose.EDIT_SUBSCRIPTION) {
            throw new IllegalStateException("pending input does not edit a subscription");
        }
        return referenceId;
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
