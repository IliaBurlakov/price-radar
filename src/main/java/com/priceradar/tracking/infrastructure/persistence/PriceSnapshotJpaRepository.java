package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.SnapshotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PriceSnapshotJpaRepository extends JpaRepository<PriceSnapshotEntity, UUID> {

    Optional<PriceSnapshotEntity> findByCheckId(UUID checkId);

    Optional<PriceSnapshotEntity> findFirstByWatchTargetIdOrderByObservedAtDesc(UUID watchTargetId);

    Optional<PriceSnapshotEntity> findFirstByWatchTargetIdAndStatusAndPriceSourceOrderByObservedAtDesc(
            UUID watchTargetId,
            SnapshotStatus status,
            PriceSource priceSource
    );

    @Modifying
    @Query(value = """
            INSERT INTO price_snapshots (
                id, check_id, watch_target_id, observed_at, status, price_source,
                regular_price_minor, marketing_base_price_minor, available
            ) VALUES (
                :id, :checkId, :watchTargetId, :observedAt, :status, :priceSource,
                :regularPriceMinor, :marketingBasePriceMinor, :available
            )
            ON CONFLICT (check_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("checkId") UUID checkId,
            @Param("watchTargetId") UUID watchTargetId,
            @Param("observedAt") Instant observedAt,
            @Param("status") String status,
            @Param("priceSource") String priceSource,
            @Param("regularPriceMinor") Long regularPriceMinor,
            @Param("marketingBasePriceMinor") Long marketingBasePriceMinor,
            @Param("available") boolean available
    );
}
