package com.priceradar.user.application;

import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.user.domain.UserPricePreferences;

import java.util.UUID;

public final class UserProfile {

    private final UUID id;
    private final long telegramUserId;
    private final long telegramChatId;
    private final PriceContext priceContext;
    private final UserPricePreferences pricePreferences;

    public UserProfile(
            UUID id,
            long telegramUserId,
            long telegramChatId,
            PriceContext priceContext,
            UserPricePreferences pricePreferences
    ) {
        if (id == null || priceContext == null || pricePreferences == null) {
            throw new IllegalArgumentException("user profile fields must not be null");
        }
        if (telegramUserId <= 0 || telegramChatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        this.id = id;
        this.telegramUserId = telegramUserId;
        this.telegramChatId = telegramChatId;
        this.priceContext = priceContext;
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
        return priceContext;
    }

    public UserPricePreferences getPricePreferences() {
        return pricePreferences;
    }
}
