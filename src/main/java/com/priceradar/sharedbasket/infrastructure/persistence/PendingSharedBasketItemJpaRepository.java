package com.priceradar.sharedbasket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PendingSharedBasketItemJpaRepository
        extends JpaRepository<PendingSharedBasketItemEntity, UUID> {

    List<PendingSharedBasketItemEntity> findByImportIdOrderByPositionAsc(UUID importId);
}
