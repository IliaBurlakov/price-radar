package com.priceradar.user.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private long telegramUserId;

    @Column(name = "telegram_chat_id", nullable = false)
    private long telegramChatId;

    @Column(name = "city_name", nullable = false, length = 100)
    private String cityName;

    @Column(name = "dest", nullable = false)
    private long dest;

    @Column(name = "spp", nullable = false)
    private int spp;

    @Column(name = "wallet_discount_percent", nullable = false)
    private int walletDiscountPercent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserProfileEntity() {
    }

    public UserProfileEntity(
            UUID id,
            long telegramUserId,
            long telegramChatId,
            String cityName,
            long dest,
            int spp,
            int walletDiscountPercent,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.telegramUserId = telegramUserId;
        this.telegramChatId = telegramChatId;
        this.cityName = cityName;
        this.dest = dest;
        this.spp = spp;
        this.walletDiscountPercent = walletDiscountPercent;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public long getTelegramChatId() {
        return telegramChatId;
    }

    public String getCityName() {
        return cityName;
    }

    public long getDest() {
        return dest;
    }

    public int getSpp() {
        return spp;
    }

    public int getWalletDiscountPercent() {
        return walletDiscountPercent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void updateTelegramChatId(long telegramChatId, Instant updatedAt) {
        if (telegramChatId <= 0) {
            throw new IllegalArgumentException("telegramChatId must be positive");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
        this.telegramChatId = telegramChatId;
        this.updatedAt = updatedAt;
    }
}
