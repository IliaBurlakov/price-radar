package com.priceradar.user.application;

import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.region.domain.GeoLocation;
import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.user.domain.UserPricePreferences;

import java.util.Optional;
import java.util.UUID;

public final class UserProfile {

    private final UUID id;
    private final long telegramUserId;
    private final long telegramChatId;
    private final ResolvedLocation selectedLocation;
    private final UserPricePreferences pricePreferences;

    public UserProfile(
            UUID id,
            long telegramUserId,
            long telegramChatId,
            ResolvedLocation selectedLocation,
            UserPricePreferences pricePreferences
    ) {
        if (id == null || pricePreferences == null) {
            throw new IllegalArgumentException("user profile fields must not be null");
        }
        if (telegramUserId <= 0 || telegramChatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }
        this.id = id;
        this.telegramUserId = telegramUserId;
        this.telegramChatId = telegramChatId;
        this.selectedLocation = selectedLocation;
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
        if (selectedLocation == null) {
            throw new IllegalStateException(
                    "Price context cannot be used before region selection"
            );
        }
        return selectedLocation.toPriceContext();
    }

    public Optional<GeoLocation> getLocation() {
        return selectedLocation == null
                ? Optional.empty()
                : Optional.of(selectedLocation.getLocation());
    }

    public Optional<ResolvedLocation> getResolvedLocation() {
        return Optional.ofNullable(selectedLocation);
    }

    public UserPricePreferences getPricePreferences() {
        return pricePreferences;
    }

    public boolean isRegionSelected() {
        return selectedLocation != null;
    }
}
