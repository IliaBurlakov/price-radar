package com.priceradar.user.application;

import com.priceradar.region.application.MarketplaceRegionStore;
import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.region.domain.MarketplaceRegionCode;
import com.priceradar.user.domain.UserPricePreferences;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

public class UserProfileService {

    private final UserProfileStore profileStore;
    private final MarketplaceRegionStore regionStore;
    private final UserPricePreferences defaultPricePreferences;
    private final Clock clock;

    public UserProfileService(
            UserProfileStore profileStore,
            MarketplaceRegionStore regionStore,
            UserPricePreferences defaultPricePreferences,
            Clock clock
    ) {
        if (profileStore == null || regionStore == null
                || defaultPricePreferences == null || clock == null) {
            throw new IllegalArgumentException("user profile service dependencies must not be null");
        }
        this.profileStore = profileStore;
        this.regionStore = regionStore;
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
                        defaultRegion(),
                        defaultPricePreferences,
                        now
                ));
    }

    private MarketplaceRegion defaultRegion() {
        return regionStore.findByCode(MarketplaceRegionCode.MOSCOW)
                .filter(MarketplaceRegion::isEnabled)
                .orElseThrow(() -> new IllegalStateException("Default Moscow region is unavailable"));
    }

    private UserProfile updateChatIfNeeded(UserProfile profile, long telegramChatId, Instant now) {
        if (profile.getTelegramChatId() == telegramChatId) {
            return profile;
        }
        return profileStore.updateTelegramChat(profile, telegramChatId, now);
    }
}
