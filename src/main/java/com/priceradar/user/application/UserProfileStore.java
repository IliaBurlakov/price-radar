package com.priceradar.user.application;

import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.user.domain.UserPricePreferences;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileStore {

    Optional<UserProfile> findByTelegramUserId(long telegramUserId);

    boolean existsAndLockById(UUID userId);

    Optional<UserProfile> findByIdAndLock(UUID userId);

    UserProfile create(
            long telegramUserId,
            long telegramChatId,
            MarketplaceRegion region,
            UserPricePreferences pricePreferences,
            Instant createdAt
    );

    UserProfile updateTelegramChat(UserProfile profile, long telegramChatId, Instant updatedAt);

    UserProfile updateRegion(UserProfile profile, MarketplaceRegion region, Instant updatedAt);
}
