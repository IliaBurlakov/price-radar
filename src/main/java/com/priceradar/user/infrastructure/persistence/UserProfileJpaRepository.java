package com.priceradar.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileEntity, UUID> {

    Optional<UserProfileEntity> findByTelegramUserId(long telegramUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_profiles (
                id, telegram_user_id, telegram_chat_id, city_name, dest, spp,
                wallet_discount_percent, created_at, updated_at, version
            ) VALUES (
                :id, :telegramUserId, :telegramChatId, :cityName, :dest, :spp,
                :walletDiscountPercent, :createdAt, :updatedAt, 0
            )
            ON CONFLICT (telegram_user_id) DO UPDATE
            SET telegram_chat_id = EXCLUDED.telegram_chat_id,
                updated_at = EXCLUDED.updated_at,
                version = user_profiles.version + 1
            """, nativeQuery = true)
    int upsert(
            @Param("id") UUID id,
            @Param("telegramUserId") long telegramUserId,
            @Param("telegramChatId") long telegramChatId,
            @Param("cityName") String cityName,
            @Param("dest") long dest,
            @Param("spp") int spp,
            @Param("walletDiscountPercent") int walletDiscountPercent,
            @Param("createdAt") java.time.Instant createdAt,
            @Param("updatedAt") java.time.Instant updatedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from UserProfileEntity profile where profile.id = :userId")
    Optional<UserProfileEntity> findByIdForUpdate(@Param("userId") UUID userId);
}
