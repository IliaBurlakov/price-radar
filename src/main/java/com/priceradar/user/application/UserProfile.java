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
    private final boolean regionSelected;

    public UserProfile(
            UUID id,
            long telegramUserId,
            long telegramChatId,
            MarketplaceRegion region,
            UserPricePreferences pricePreferences
    ) {
        this(id, telegramUserId, telegramChatId, region, pricePreferences, true);
    }

    public UserProfile(
            UUID id,
            long telegramUserId,
            long telegramChatId,
            MarketplaceRegion region,
            UserPricePreferences pricePreferences,
            boolean regionSelected
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

    public PriceContext getPriceContext() {
        if (!regionSelected) {
            throw new IllegalStateException(
                    "Price context cannot be used before region selection"
            );
        }
        return region.toPriceContext();
    }

    public MarketplaceRegion getRegion() {
        return region;
    }

    public UserPricePreferences getPricePreferences() {
        return pricePreferences;
    }

    public boolean isRegionSelected() {
        return regionSelected;
    }
}
