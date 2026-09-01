package com.priceradar.user.application;

import com.priceradar.user.domain.UserPricePreferences;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

public class UserProfileService {

    private final UserProfileStore profileStore;
    private final UserPricePreferences defaultPricePreferences;
    private final Clock clock;

    public UserProfileService(
            UserProfileStore profileStore,
            UserPricePreferences defaultPricePreferences,
            Clock clock
    ) {
        if (profileStore == null || defaultPricePreferences == null || clock == null) {
            throw new IllegalArgumentException("user profile service dependencies must not be null");
        }
        this.profileStore = profileStore;
        this.defaultPricePreferences = defaultPricePreferences;
        this.clock = clock;
    }

    @Transactional
    public UserProfile getOrCreate(long telegramUserId, long telegramChatId) {
        if (telegramUserId <= 0 || telegramChatId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers must be positive");
        }

        Instant now = clock.instant();
        return profileStore.findByTelegramUserId(telegramUserId)
                .map(profile -> updateChatIfNeeded(profile, telegramChatId, now))
                .orElseGet(() -> profileStore.create(
                        telegramUserId,
                        telegramChatId,
                        defaultPricePreferences,
                        now
                ));
    }

    @Transactional
    public UserProfile updateWalletDiscountPercent(
            long telegramUserId,
            long telegramChatId,
            int walletDiscountPercent
    ) {
        UserPricePreferences pricePreferences = new UserPricePreferences(walletDiscountPercent);
        UserProfile profile = getOrCreate(telegramUserId, telegramChatId);
        if (profile.getPricePreferences().equals(pricePreferences)) {
            return profile;
        }
        return profileStore.updatePricePreferences(profile, pricePreferences, clock.instant());
    }

    private UserProfile updateChatIfNeeded(UserProfile profile, long telegramChatId, Instant now) {
        if (profile.getTelegramChatId() == telegramChatId) {
            return profile;
        }
        return profileStore.updateTelegramChat(profile, telegramChatId, now);
    }
}
