package com.priceradar.sharedbasket.infrastructure.persistence;

import com.priceradar.sharedbasket.application.PendingSharedBasketImport;
import com.priceradar.sharedbasket.application.PendingSharedBasketImportStore;
import com.priceradar.sharedbasket.application.PendingSharedBasketItem;
import com.priceradar.sharedbasket.application.PendingUnavailableSharedBasketItem;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaPendingSharedBasketImportStore implements PendingSharedBasketImportStore {

    private final PendingSharedBasketImportJpaRepository importRepository;
    private final PendingSharedBasketItemJpaRepository itemRepository;
    private final PendingUnavailableSharedBasketItemJpaRepository unavailableItemRepository;

    public JpaPendingSharedBasketImportStore(
            PendingSharedBasketImportJpaRepository importRepository,
            PendingSharedBasketItemJpaRepository itemRepository,
            PendingUnavailableSharedBasketItemJpaRepository unavailableItemRepository
    ) {
        this.importRepository = importRepository;
        this.itemRepository = itemRepository;
        this.unavailableItemRepository = unavailableItemRepository;
    }

    @Override
    @Transactional
    public PendingSharedBasketImport save(PendingSharedBasketImport pendingImport, Instant now) {
        importRepository.deleteExpired(now);
        importRepository.deleteByUserId(pendingImport.getUserId());
        importRepository.save(new PendingSharedBasketImportEntity(
                pendingImport.getId(), pendingImport.getUserId(), pendingImport.getRegionCode(),
                pendingImport.getFoundItems(),
                pendingImport.getAvailableItems(), pendingImport.getUnresolvedItems(),
                pendingImport.getCreatedAt(), pendingImport.getExpiresAt()
        ));
        itemRepository.saveAll(pendingImport.getItems().stream()
                .map(item -> new PendingSharedBasketItemEntity(
                        UUID.randomUUID(), pendingImport.getId(), item.getPosition(),
                        item.getWatchTargetId(), item.getSnapshotId(), item.getTitle().orElse(null)
                ))
                .toList());
        unavailableItemRepository.saveAll(pendingImport.getUnavailableItems().stream()
                .map(item -> new PendingUnavailableSharedBasketItemEntity(
                        UUID.randomUUID(), pendingImport.getId(), item.getPosition(), item.getDisplayName()
                ))
                .toList());
        return pendingImport;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PendingSharedBasketImport> findOwned(UUID importId, UUID userId) {
        return importRepository.findByIdAndUserId(importId, userId).map(entity ->
                new PendingSharedBasketImport(
                        entity.getId(), entity.getUserId(), entity.getRegionCode(),
                        entity.getFoundItems(),
                        entity.getAvailableItems(), entity.getUnresolvedItems(),
                        unavailableItemRepository.findByImportIdOrderByPositionAsc(importId).stream()
                                .map(item -> new PendingUnavailableSharedBasketItem(
                                        item.getPosition(), item.getDisplayName()
                                ))
                                .toList(),
                        itemRepository.findByImportIdOrderByPositionAsc(importId).stream()
                                .map(item -> new PendingSharedBasketItem(
                                        item.getPosition(), item.getWatchTargetId(), item.getSnapshotId(),
                                        Optional.ofNullable(item.getProductTitle())
                                ))
                                .toList(),
                        entity.getCreatedAt(), entity.getExpiresAt()
                ));
    }

    @Override
    @Transactional
    public void remove(UUID importId, UUID userId) {
        importRepository.deleteByIdAndUserId(importId, userId);
    }
}
