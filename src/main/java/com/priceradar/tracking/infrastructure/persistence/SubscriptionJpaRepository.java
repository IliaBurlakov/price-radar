package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import com.priceradar.tracking.domain.NotificationMode;
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

    Optional<SubscriptionEntity> findByIdAndStatus(
            UUID subscriptionId,
            SubscriptionStatus status
    );

    @Query("""
            SELECT subscription.id
            FROM SubscriptionEntity subscription
            WHERE subscription.watchTargetId = :watchTargetId
              AND subscription.status = :status
            ORDER BY subscription.createdAt, subscription.id
            """)
    List<UUID> findIdsByWatchTargetIdAndStatus(
            @Param("watchTargetId") UUID watchTargetId,
            @Param("status") SubscriptionStatus status
    );

    long countByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    List<SubscriptionEntity> findByUserIdAndStatusOrderByCreatedAtAscIdAsc(
            UUID userId,
            SubscriptionStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SubscriptionEntity subscription
            SET subscription.notificationReferencePriceMinor = :notificationReferencePriceMinor,
                subscription.lastProcessedPriceObservedAt = :lastProcessedPriceObservedAt,
                subscription.thresholdState = :thresholdState,
                subscription.thresholdObservedAt = :thresholdObservedAt,
                subscription.version = subscription.version + 1
            WHERE subscription.id = :subscriptionId
              AND subscription.status = :activeStatus
              AND subscription.version = :expectedVersion
            """)
    int updateNotificationStateIfActive(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("notificationReferencePriceMinor") Long notificationReferencePriceMinor,
            @Param("lastProcessedPriceObservedAt") Instant lastProcessedPriceObservedAt,
            @Param("thresholdState") ThresholdState thresholdState,
            @Param("thresholdObservedAt") Instant thresholdObservedAt,
            @Param("activeStatus") SubscriptionStatus activeStatus,
            @Param("expectedVersion") long expectedVersion
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SubscriptionEntity subscription
            SET subscription.notificationMode = :notificationMode,
                subscription.targetPriceMinor = :targetPriceMinor,
                subscription.notificationReferencePriceMinor = :notificationReferencePriceMinor,
                subscription.lastProcessedPriceObservedAt = :lastProcessedPriceObservedAt,
                subscription.thresholdState = :thresholdState,
                subscription.thresholdObservedAt = :thresholdObservedAt,
                subscription.version = subscription.version + 1
            WHERE subscription.id = :subscriptionId
              AND subscription.status = :activeStatus
              AND subscription.version = :expectedVersion
            """)
    int updateConditionIfActive(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("notificationMode") NotificationMode notificationMode,
            @Param("targetPriceMinor") Long targetPriceMinor,
            @Param("notificationReferencePriceMinor") Long notificationReferencePriceMinor,
            @Param("lastProcessedPriceObservedAt") Instant lastProcessedPriceObservedAt,
            @Param("thresholdState") ThresholdState thresholdState,
            @Param("thresholdObservedAt") Instant thresholdObservedAt,
            @Param("activeStatus") SubscriptionStatus activeStatus,
            @Param("expectedVersion") long expectedVersion
    );

    @Query(value = """
            SELECT
                MIN(snapshot.regular_price_minor) AS "minimumPriceMinor",
                (
                    SELECT latest.regular_price_minor
                    FROM price_snapshots latest
                    WHERE latest.watch_target_id = subscription.watch_target_id
                      AND latest.observed_at >= subscription.created_at
                      AND latest.observed_at <= :observedToInclusive
                      AND latest.status = 'REGULAR_PRICE'
                      AND latest.price_source = 'PRODUCT'
                      AND latest.regular_price_minor > 0
                    ORDER BY latest.observed_at DESC, latest.id DESC
                    LIMIT 1
                ) AS "latestPriceMinor",
                MAX(snapshot.observed_at) AS "latestObservedAt"
            FROM subscriptions subscription
            LEFT JOIN price_snapshots snapshot
              ON snapshot.watch_target_id = subscription.watch_target_id
             AND snapshot.observed_at >= subscription.created_at
             AND snapshot.observed_at <= :observedToInclusive
             AND snapshot.status = 'REGULAR_PRICE'
             AND snapshot.price_source = 'PRODUCT'
             AND snapshot.regular_price_minor > 0
            WHERE subscription.id = :subscriptionId
              AND subscription.user_id = :userId
              AND subscription.status = 'ACTIVE'
            GROUP BY subscription.id, subscription.watch_target_id, subscription.created_at
            """, nativeQuery = true)
    Optional<SubscriptionPriceHistoryProjection> findValidPriceHistory(
            @Param("userId") UUID userId,
            @Param("subscriptionId") UUID subscriptionId,
            @Param("observedToInclusive") Instant observedToInclusive
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
            ORDER BY s.created_at, s.id
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
                wt.city_name AS "cityName",
                wt.dest AS "dest",
                wt.spp AS "spp",
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
