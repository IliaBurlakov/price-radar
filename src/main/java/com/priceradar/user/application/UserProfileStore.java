package com.priceradar.user.application;

import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.user.domain.UserPricePreferences;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileStore {

    Optional<UserProfile> findByTelegramUserId(long telegramUserId);

    boolean existsAndLockById(UUID userId);

    UserProfile create(
            long telegramUserId,
            long telegramChatId,
            PriceContext priceContext,
            UserPricePreferences pricePreferences,
            Instant createdAt
    );

    UserProfile updateTelegramChat(UserProfile profile, long telegramChatId, Instant updatedAt);
}
