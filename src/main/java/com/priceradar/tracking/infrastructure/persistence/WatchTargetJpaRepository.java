package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.tracking.domain.VariantKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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
                city_name, dest, spp, next_check_at, created_at, version
            ) VALUES (
                :id, :productId, :variantKind, :variantValue, :variantDisplayName,
                :cityName, :dest, :spp, :nextCheckAt, :createdAt, 0
            )
            ON CONFLICT (product_id, variant_kind, variant_value, dest, spp) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("productId") UUID productId,
            @Param("variantKind") String variantKind,
            @Param("variantValue") String variantValue,
            @Param("variantDisplayName") String variantDisplayName,
            @Param("cityName") String cityName,
            @Param("dest") long dest,
            @Param("spp") int spp,
            @Param("nextCheckAt") Instant nextCheckAt,
            @Param("createdAt") Instant createdAt
    );

    @Query(value = """
            SELECT
                target.id AS "watchTargetId",
                target.product_id AS "productId",
                product.marketplace AS "marketplace",
                product.external_product_id AS "externalProductId",
                target.variant_kind AS "variantKind",
                target.variant_value AS "variantValue",
                target.dest AS "dest",
                target.spp AS "spp",
                target.next_check_at AS "nextCheckAt"
            FROM watch_targets target
            JOIN products product ON product.id = target.product_id
            WHERE target.next_check_at <= :now
              AND EXISTS (
                  SELECT 1
                  FROM subscriptions subscription
                  WHERE subscription.watch_target_id = target.id
                    AND subscription.status = 'ACTIVE'
              )
            ORDER BY target.next_check_at, target.id
            LIMIT :limit
            """, nativeQuery = true)
    List<DueWatchTargetProjection> findDueWithActiveSubscriptions(
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    @Modifying
    @Query(value = """
            UPDATE watch_targets
            SET last_checked_at = :completedAt,
                last_successful_at = :completedAt,
                next_check_at = :nextCheckAt,
                version = version + 1
            WHERE id = :watchTargetId
            """, nativeQuery = true)
    int markSuccessfulCheck(
            @Param("watchTargetId") UUID watchTargetId,
            @Param("completedAt") Instant completedAt,
            @Param("nextCheckAt") Instant nextCheckAt
    );

    @Modifying
    @Query(value = """
            UPDATE watch_targets
            SET last_checked_at = :completedAt,
                next_check_at = :nextCheckAt,
                version = version + 1
            WHERE id = :watchTargetId
            """, nativeQuery = true)
    int markFailedCheck(
            @Param("watchTargetId") UUID watchTargetId,
            @Param("completedAt") Instant completedAt,
            @Param("nextCheckAt") Instant nextCheckAt
    );
}
