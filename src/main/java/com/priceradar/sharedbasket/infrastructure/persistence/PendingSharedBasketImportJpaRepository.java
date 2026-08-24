package com.priceradar.sharedbasket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PendingSharedBasketImportJpaRepository
        extends JpaRepository<PendingSharedBasketImportEntity, UUID> {

    Optional<PendingSharedBasketImportEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("DELETE FROM PendingSharedBasketImportEntity pending WHERE pending.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
