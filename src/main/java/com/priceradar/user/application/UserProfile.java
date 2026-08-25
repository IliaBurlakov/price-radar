package com.priceradar.user.application;

import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.user.domain.UserPricePreferences;

import java.util.UUID;

public final class UserProfile {

    private final UUID id;
    private final long telegramUserId;
    private final long telegramChatId;
    private final MarketplaceRegion region;
    private final UserPricePreferences pricePreferences;

    public UserProfile(
            UUID id,
            long telegramUserId,
            long telegramChatId,
            MarketplaceRegion region,
            UserPricePreferences pricePreferences
    ) {
        if (id == null || region == null || pricePreferences == null) {
            throw new IllegalArgumentException("user profile fields must not be null");
        }
        if (telegramUserId <= 0 || telegramChatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        this.id = id;
        this.telegramUserId = telegramUserId;
        this.telegramChatId = telegramChatId;
        this.region = region;
        this.pricePreferences = pricePreferences;
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

    public PriceContext getPriceContext() {
        return region.toPriceContext();
    }

    public MarketplaceRegion getRegion() {
        return region;
    }

    public UserPricePreferences getPricePreferences() {
        return pricePreferences;
    }
}
