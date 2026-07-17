package com.priceradar.user.application;

import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.user.domain.UserPricePreferences;

import java.time.Instant;
import java.util.Optional;

public interface UserProfileStore {

    Optional<UserProfile> findByTelegramUserId(long telegramUserId);

    UserProfile create(
            long telegramUserId,
            long telegramChatId,
            PriceContext priceContext,
            UserPricePreferences pricePreferences,
            Instant createdAt
    );

    UserProfile updateTelegramChat(UserProfile profile, long telegramChatId, Instant updatedAt);
}
