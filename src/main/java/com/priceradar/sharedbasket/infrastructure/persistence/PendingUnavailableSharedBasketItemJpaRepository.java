package com.priceradar.sharedbasket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PendingUnavailableSharedBasketItemJpaRepository
        extends JpaRepository<PendingUnavailableSharedBasketItemEntity, UUID> {

    List<PendingUnavailableSharedBasketItemEntity> findByImportIdOrderByPositionAsc(UUID importId);
}
