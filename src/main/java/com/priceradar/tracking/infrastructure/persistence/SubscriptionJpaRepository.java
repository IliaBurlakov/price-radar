package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByUserIdAndWatchTargetIdAndStatus(
            UUID userId,
            UUID watchTargetId,
            SubscriptionStatus status
    );

    Optional<SubscriptionEntity> findByIdAndUserIdAndStatus(
            UUID subscriptionId,
            UUID userId,
            SubscriptionStatus status
    );

    long countByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SubscriptionEntity subscription
            SET subscription.baselinePriceMinor = :baselinePriceMinor,
                subscription.baselineObservedAt = :baselineObservedAt,
                subscription.thresholdState = :thresholdState,
                subscription.version = subscription.version + 1
            WHERE subscription.id = :subscriptionId
              AND subscription.status = :activeStatus
            """)
    int updateNotificationStateIfActive(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("baselinePriceMinor") Long baselinePriceMinor,
            @Param("baselineObservedAt") Instant baselineObservedAt,
            @Param("thresholdState") ThresholdState thresholdState,
            @Param("activeStatus") SubscriptionStatus activeStatus
    );

    @Query(value = """
            SELECT
                s.id AS "subscriptionId",
                p.external_product_id AS "nmId",
                p.title AS "title",
                p.brand AS "brand",
                p.canonical_url AS "canonicalUrl",
                wt.variant_display_name AS "variantDisplayName",
                s.notification_mode AS "notificationMode",
                s.target_price_minor AS "targetPriceMinor",
                s.created_at AS "trackingStartedAt",
                latest.status AS "snapshotStatus",
                latest.price_source AS "priceSource",
                latest.regular_price_minor AS "regularPriceMinor",
                latest.observed_at AS "observedAt"
            FROM subscriptions s
            JOIN watch_targets wt ON wt.id = s.watch_target_id
            JOIN products p ON p.id = wt.product_id
            LEFT JOIN LATERAL (
                SELECT
                    ps.status,
                    ps.price_source,
                    ps.regular_price_minor,
                    ps.observed_at
                FROM price_snapshots ps
                WHERE ps.watch_target_id = wt.id
                ORDER BY ps.observed_at DESC, ps.id DESC
                LIMIT 1
            ) latest ON TRUE
            WHERE s.user_id = :userId
              AND s.status = 'ACTIVE'
            ORDER BY s.created_at DESC, s.id
            """, nativeQuery = true)
    List<TrackedSubscriptionProjection> findActiveTrackedItems(
            @Param("userId") UUID userId
    );

    @Query(value = """
            SELECT
                s.id AS "subscriptionId",
                p.external_product_id AS "nmId",
                p.title AS "title",
                p.brand AS "brand",
                p.canonical_url AS "canonicalUrl",
                wt.variant_display_name AS "variantDisplayName",
                latest.status AS "snapshotStatus",
                latest.price_source AS "priceSource",
                latest.regular_price_minor AS "regularPriceMinor",
                latest.marketing_base_price_minor AS "marketingBasePriceMinor",
                latest.observed_at AS "observedAt"
            FROM subscriptions s
            JOIN watch_targets wt ON wt.id = s.watch_target_id
            JOIN products p ON p.id = wt.product_id
            LEFT JOIN LATERAL (
                SELECT
                    ps.status,
                    ps.price_source,
                    ps.regular_price_minor,
                    ps.marketing_base_price_minor,
                    ps.observed_at
                FROM price_snapshots ps
                WHERE ps.watch_target_id = wt.id
                ORDER BY ps.observed_at DESC, ps.id DESC
                LIMIT 1
            ) latest ON TRUE
            WHERE s.id = :subscriptionId
              AND s.user_id = :userId
              AND s.status = 'ACTIVE'
            """, nativeQuery = true)
    Optional<LatestSnapshotProjection> findLatestSnapshotActiveOwned(
            @Param("userId") UUID userId,
            @Param("subscriptionId") UUID subscriptionId
    );
}
