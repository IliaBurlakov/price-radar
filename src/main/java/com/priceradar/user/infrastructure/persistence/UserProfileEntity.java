package com.priceradar.user.infrastructure.persistence;

import com.priceradar.region.domain.MarketplaceRegionCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", nullable = false, length = 32)
    private MarketplaceRegionCode regionCode;

    @Column(name = "wallet_discount_percent", nullable = false)
    private int walletDiscountPercent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "region_selected", nullable = false)
    private boolean regionSelected;

    protected UserProfileEntity() {
    }

    public UserProfileEntity(
            UUID id,
            long telegramUserId,
            long telegramChatId,
            MarketplaceRegionCode regionCode,
            int walletDiscountPercent,
            Instant createdAt,
            Instant updatedAt,
            boolean regionSelected
    ) {
        this.id = id;
        this.telegramUserId = telegramUserId;
        this.telegramChatId = telegramChatId;
        this.regionCode = regionCode;
        this.walletDiscountPercent = walletDiscountPercent;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.regionSelected = regionSelected;
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

    public MarketplaceRegionCode getRegionCode() {
        return regionCode;
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

    public boolean isRegionSelected() {
        return regionSelected;
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

    public void updateRegion(MarketplaceRegionCode regionCode, Instant updatedAt) {
        if (regionCode == null || updatedAt == null) {
            throw new IllegalArgumentException("region update fields must not be null");
        }
        this.regionCode = regionCode;
        this.regionSelected = true;
        this.updatedAt = updatedAt;
    }
}
