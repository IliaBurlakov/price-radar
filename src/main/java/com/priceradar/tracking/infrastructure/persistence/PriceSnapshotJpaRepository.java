package com.priceradar.tracking.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PriceSnapshotJpaRepository extends JpaRepository<PriceSnapshotEntity, UUID> {

    Optional<PriceSnapshotEntity> findByCheckId(UUID checkId);

    Optional<PriceSnapshotEntity> findFirstByWatchTargetIdOrderByObservedAtDesc(UUID watchTargetId);
}
