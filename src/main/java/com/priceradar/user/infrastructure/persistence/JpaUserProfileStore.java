package com.priceradar.user.infrastructure.persistence;

import com.priceradar.region.application.MarketplaceRegionStore;
import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.application.UserProfileStore;
import com.priceradar.user.domain.UserPricePreferences;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaUserProfileStore implements UserProfileStore {

    private final UserProfileJpaRepository profileRepository;
    private final MarketplaceRegionStore regionStore;

    public JpaUserProfileStore(
            UserProfileJpaRepository profileRepository,
            MarketplaceRegionStore regionStore
    ) {
        this.profileRepository = profileRepository;
        this.regionStore = regionStore;
    }

    @Override
    public Optional<UserProfile> findByTelegramUserId(long telegramUserId) {
        return profileRepository.findByTelegramUserId(telegramUserId).map(this::toProfile);
    }

    @Override
    public boolean existsAndLockById(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return profileRepository.findByIdForUpdate(userId).isPresent();
    }

    @Override
    public Optional<UserProfile> findByIdAndLock(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        return profileRepository.findByIdForUpdate(userId).map(this::toProfile);
    }

    @Override
    public UserProfile create(
            long telegramUserId,
            long telegramChatId,
            MarketplaceRegion region,
            UserPricePreferences pricePreferences,
            Instant createdAt
    ) {
        profileRepository.upsert(
                UUID.randomUUID(),
                telegramUserId,
                telegramChatId,
                region.getCode().name(),
                pricePreferences.getWalletDiscountPercent(),
                createdAt,
                createdAt
        );
        return profileRepository.findByTelegramUserId(telegramUserId)
                .map(this::toProfile)
                .orElseThrow(() -> new IllegalStateException("User profile upsert did not return a profile"));
    }

    @Override
    public UserProfile updateTelegramChat(UserProfile profile, long telegramChatId, Instant updatedAt) {
        UserProfileEntity entity = profileRepository.findById(profile.getId())
                .orElseThrow(() -> new IllegalStateException("User profile no longer exists"));
        entity.updateTelegramChatId(telegramChatId, updatedAt);
        return toProfile(entity);
    }

    @Override
    public UserProfile updateRegion(UserProfile profile, MarketplaceRegion region, Instant updatedAt) {
        UserProfileEntity entity = profileRepository.findById(profile.getId())
                .orElseThrow(() -> new IllegalStateException("User profile no longer exists"));
        entity.updateRegion(region.getCode(), updatedAt);
        return toProfile(entity);
    }

    private UserProfile toProfile(UserProfileEntity entity) {
        return new UserProfile(
                entity.getId(),
                entity.getTelegramUserId(),
                entity.getTelegramChatId(),
                regionStore.findByCode(entity.getRegionCode())
                        .orElseThrow(() -> new IllegalStateException("User region no longer exists")),
                new UserPricePreferences(entity.getWalletDiscountPercent())
        );
    }
}
