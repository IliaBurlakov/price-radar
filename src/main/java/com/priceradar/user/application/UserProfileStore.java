package com.priceradar.user.application;

import com.priceradar.region.domain.ResolvedLocation;
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
            UserPricePreferences pricePreferences,
            Instant createdAt
    );

    UserProfile updateTelegramChat(UserProfile profile, long telegramChatId, Instant updatedAt);

    UserProfile updateLocation(UserProfile profile, ResolvedLocation location, Instant updatedAt);
}
