package com.priceradar.user.infrastructure.persistence;

import com.priceradar.user.domain.UserPricePreferences;

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

    @Column(name = "location_id")
    private UUID locationId;

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
            UUID locationId,
            int walletDiscountPercent,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.telegramUserId = telegramUserId;
        this.telegramChatId = telegramChatId;
        this.locationId = locationId;
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

    public UUID getLocationId() {
        return locationId;
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

    public void updateLocation(UUID locationId, Instant updatedAt) {
        if (locationId == null || updatedAt == null) {
            throw new IllegalArgumentException("location update fields must not be null");
        }
        this.locationId = locationId;
        this.updatedAt = updatedAt;
    }

    public void updateWalletDiscountPercent(int walletDiscountPercent, Instant updatedAt) {
        if (walletDiscountPercent < UserPricePreferences.MIN_WALLET_DISCOUNT_PERCENT
                || walletDiscountPercent > UserPricePreferences.MAX_WALLET_DISCOUNT_PERCENT) {
            throw new IllegalArgumentException("walletDiscountPercent must be between 2 and 20");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
        this.walletDiscountPercent = walletDiscountPercent;
        this.updatedAt = updatedAt;
    }
}
