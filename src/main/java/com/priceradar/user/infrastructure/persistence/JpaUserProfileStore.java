package com.priceradar.user.infrastructure.persistence;

import com.priceradar.region.application.GeoLocationCatalog;
import com.priceradar.region.domain.ResolvedLocation;
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
    private final GeoLocationCatalog locationCatalog;

    public JpaUserProfileStore(
            UserProfileJpaRepository profileRepository,
            GeoLocationCatalog locationCatalog
    ) {
        this.profileRepository = profileRepository;
        this.locationCatalog = locationCatalog;
    }

    @Override
    public Optional<UserProfile> findByTelegramUserId(long telegramUserId) {
        return profileRepository.findByTelegramUserId(telegramUserId).map(this::toProfile);
    }

    @Override
    public Optional<UserProfile> findById(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return profileRepository.findById(userId).map(this::toProfile);
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
            UserPricePreferences pricePreferences,
            int activeSubscriptionLimit,
            Instant createdAt
    ) {
        profileRepository.upsert(
                UUID.randomUUID(),
                telegramUserId,
                telegramChatId,
                pricePreferences.getWalletDiscountPercent(),
                activeSubscriptionLimit,
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
    public UserProfile updateLocation(UserProfile profile, ResolvedLocation location, Instant updatedAt) {
        UserProfileEntity entity = profileRepository.findById(profile.getId())
                .orElseThrow(() -> new IllegalStateException("User profile no longer exists"));
        entity.updateLocation(location.getLocation().getId(), updatedAt);
        return toProfile(entity);
    }

    @Override
    public UserProfile updatePricePreferences(
            UserProfile profile,
            UserPricePreferences pricePreferences,
            Instant updatedAt
    ) {
        if (profile == null || pricePreferences == null || updatedAt == null) {
            throw new IllegalArgumentException("price preference update fields must not be null");
        }
        UserProfileEntity entity = profileRepository.findById(profile.getId())
                .orElseThrow(() -> new IllegalStateException("User profile no longer exists"));
        entity.updateWalletDiscountPercent(
                pricePreferences.getWalletDiscountPercent(), updatedAt
        );
        return toProfile(entity);
    }

    private UserProfile toProfile(UserProfileEntity entity) {
        return new UserProfile(
                entity.getId(),
                entity.getTelegramUserId(),
                entity.getTelegramChatId(),
                entity.getLocationId() == null ? null : locationCatalog.findById(entity.getLocationId())
                        .orElseThrow(() -> new IllegalStateException("User location no longer exists")),
                new UserPricePreferences(entity.getWalletDiscountPercent()),
                entity.getActiveSubscriptionLimit()
        );
    }
}
