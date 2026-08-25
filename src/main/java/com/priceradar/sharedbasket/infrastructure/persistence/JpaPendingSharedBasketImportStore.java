package com.priceradar.sharedbasket.infrastructure.persistence;

import com.priceradar.sharedbasket.application.PendingSharedBasketImport;
import com.priceradar.sharedbasket.application.PendingSharedBasketImportStore;
import com.priceradar.sharedbasket.application.PendingSharedBasketItem;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaPendingSharedBasketImportStore implements PendingSharedBasketImportStore {

    private final PendingSharedBasketImportJpaRepository importRepository;
    private final PendingSharedBasketItemJpaRepository itemRepository;

    public JpaPendingSharedBasketImportStore(
            PendingSharedBasketImportJpaRepository importRepository,
            PendingSharedBasketItemJpaRepository itemRepository
    ) {
        this.importRepository = importRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    @Transactional
    public PendingSharedBasketImport save(PendingSharedBasketImport pendingImport, Instant now) {
        importRepository.deleteExpired(now);
        importRepository.deleteByUserId(pendingImport.getUserId());
        importRepository.save(new PendingSharedBasketImportEntity(
                pendingImport.getId(), pendingImport.getUserId(), pendingImport.getFoundItems(),
                pendingImport.getSkippedItems(), pendingImport.getCreatedAt(), pendingImport.getExpiresAt()
        ));
        itemRepository.saveAll(pendingImport.getItems().stream()
                .map(item -> new PendingSharedBasketItemEntity(
                        UUID.randomUUID(), pendingImport.getId(), item.getPosition(),
                        item.getWatchTargetId(), item.getSnapshotId(), item.getTitle().orElse(null)
                ))
                .toList());
        return pendingImport;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PendingSharedBasketImport> findOwned(UUID importId, UUID userId) {
        return importRepository.findByIdAndUserId(importId, userId).map(entity ->
                new PendingSharedBasketImport(
                        entity.getId(), entity.getUserId(), entity.getFoundItems(), entity.getSkippedItems(),
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
