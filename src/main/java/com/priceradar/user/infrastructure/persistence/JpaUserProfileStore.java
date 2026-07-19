package com.priceradar.user.infrastructure.persistence;

import com.priceradar.pricing.domain.PriceContext;
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

    public JpaUserProfileStore(UserProfileJpaRepository profileRepository) {
        this.profileRepository = profileRepository;
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
    public UserProfile create(
            long telegramUserId,
            long telegramChatId,
            PriceContext priceContext,
            UserPricePreferences pricePreferences,
            Instant createdAt
    ) {
        UserProfileEntity entity = new UserProfileEntity(
                UUID.randomUUID(),
                telegramUserId,
                telegramChatId,
                priceContext.getCityName(),
                priceContext.getDest(),
                priceContext.getSpp(),
                pricePreferences.getWalletDiscountPercent(),
                createdAt,
                createdAt
        );
        return toProfile(profileRepository.save(entity));
    }

    @Override
    public UserProfile updateTelegramChat(UserProfile profile, long telegramChatId, Instant updatedAt) {
        UserProfileEntity entity = profileRepository.findById(profile.getId())
                .orElseThrow(() -> new IllegalStateException("User profile no longer exists"));
        entity.updateTelegramChatId(telegramChatId, updatedAt);
        return toProfile(entity);
    }

    private UserProfile toProfile(UserProfileEntity entity) {
        return new UserProfile(
                entity.getId(),
                entity.getTelegramUserId(),
                entity.getTelegramChatId(),
                new PriceContext(entity.getCityName(), entity.getDest(), entity.getSpp()),
                new UserPricePreferences(entity.getWalletDiscountPercent())
        );
    }
}
