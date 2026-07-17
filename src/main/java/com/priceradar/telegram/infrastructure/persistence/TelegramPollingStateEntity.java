package com.priceradar.telegram.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "telegram_polling_state")
public class TelegramPollingStateEntity {

    @Id
    @Column(name = "bot_key", length = 100)
    private String botKey;

    @Column(name = "last_confirmed_update_id")
    private Long lastConfirmedUpdateId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TelegramPollingStateEntity() {
    }

    public TelegramPollingStateEntity(
            String botKey,
            Long lastConfirmedUpdateId,
            Instant updatedAt
    ) {
        this.botKey = botKey;
        this.lastConfirmedUpdateId = lastConfirmedUpdateId;
        this.updatedAt = updatedAt;
    }

    public String getBotKey() {
        return botKey;
    }

    public Long getLastConfirmedUpdateId() {
        return lastConfirmedUpdateId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean confirm(long updateId, Instant confirmedAt) {
        if (updateId < 0) {
            throw new IllegalArgumentException("updateId must be non-negative");
        }
        if (confirmedAt == null) {
            throw new IllegalArgumentException("confirmedAt must not be null");
        }
        if (lastConfirmedUpdateId != null && updateId <= lastConfirmedUpdateId) {
            return false;
        }
        lastConfirmedUpdateId = updateId;
        updatedAt = confirmedAt;
        return true;
    }
}
