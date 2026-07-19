package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.tracking.domain.VariantKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WatchTargetJpaRepository extends JpaRepository<WatchTargetEntity, UUID> {

    Optional<WatchTargetEntity> findByProductIdAndVariantKindAndVariantValueAndDestAndSpp(
            UUID productId,
            VariantKind variantKind,
            String variantValue,
            long dest,
            int spp
    );

    @Modifying
    @Query(value = """
            INSERT INTO watch_targets (
                id, product_id, variant_kind, variant_value, variant_display_name,
                dest, spp, next_check_at, created_at, version
            ) VALUES (
                :id, :productId, :variantKind, :variantValue, :variantDisplayName,
                :dest, :spp, :nextCheckAt, :createdAt, 0
            )
            ON CONFLICT (product_id, variant_kind, variant_value, dest, spp) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("productId") UUID productId,
            @Param("variantKind") String variantKind,
            @Param("variantValue") String variantValue,
            @Param("variantDisplayName") String variantDisplayName,
            @Param("dest") long dest,
            @Param("spp") int spp,
            @Param("nextCheckAt") Instant nextCheckAt,
            @Param("createdAt") Instant createdAt
    );
}
