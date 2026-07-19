package com.priceradar.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileEntity, UUID> {

    Optional<UserProfileEntity> findByTelegramUserId(long telegramUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from UserProfileEntity profile where profile.id = :userId")
    Optional<UserProfileEntity> findByIdForUpdate(@Param("userId") UUID userId);
}
