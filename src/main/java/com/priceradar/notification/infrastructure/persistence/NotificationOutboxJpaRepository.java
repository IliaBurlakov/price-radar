package com.priceradar.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxJpaRepository
        extends JpaRepository<NotificationOutboxEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO notification_outbox (
                id, subscription_id, snapshot_id, notification_type,
                idempotency_key, payload, status, attempt_count,
                next_attempt_at, created_at
            ) VALUES (
                :id, :subscriptionId, :snapshotId, :notificationType,
                :idempotencyKey, CAST(:payload AS jsonb), 'PENDING', 0,
                :nextAttemptAt, :createdAt
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertPending(
            @Param("id") UUID id,
            @Param("subscriptionId") UUID subscriptionId,
            @Param("snapshotId") UUID snapshotId,
            @Param("notificationType") String notificationType,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("payload") String payload,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("createdAt") Instant createdAt
    );

    @Query(value = """
            SELECT
                outbox.id AS "outboxId",
                outbox.notification_type AS "notificationType",
                outbox.attempt_count AS "attemptCount",
                outbox.next_attempt_at AS "nextAttemptAt",
                (subscription.status = 'ACTIVE') AS "activeSubscription",
                profile.telegram_chat_id AS "telegramChatId",
                product.external_product_id AS "externalProductId",
                product.title AS "productTitle",
                product.brand AS "brand",
                target.variant_display_name AS "variantDisplayName",
                target.city_name AS "cityName",
                product.canonical_url AS "canonicalUrl",
                profile.wallet_discount_percent AS "walletDiscountPercent",
                subscription.target_price_minor AS "targetPriceMinor",
                CAST(outbox.payload AS text) AS "payload"
            FROM notification_outbox outbox
            JOIN subscriptions subscription ON subscription.id = outbox.subscription_id
            JOIN user_profiles profile ON profile.id = subscription.user_id
            JOIN watch_targets target ON target.id = subscription.watch_target_id
            JOIN products product ON product.id = target.product_id
            WHERE outbox.status IN ('PENDING', 'RETRY')
              AND outbox.next_attempt_at <= :now
            ORDER BY outbox.next_attempt_at, outbox.id
            LIMIT :limit
            """, nativeQuery = true)
    List<NotificationDeliveryProjection> findDueDeliveries(
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET next_attempt_at = :claimUntil
            WHERE id = :outboxId
              AND status IN ('PENDING', 'RETRY')
              AND next_attempt_at = :expectedNextAttemptAt
              AND next_attempt_at <= :now
            """, nativeQuery = true)
    int claimForDelivery(
            @Param("outboxId") UUID outboxId,
            @Param("expectedNextAttemptAt") Instant expectedNextAttemptAt,
            @Param("now") Instant now,
            @Param("claimUntil") Instant claimUntil
    );

    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'SENT', sent_at = :sentAt, last_error_code = NULL
            WHERE id = :outboxId
              AND status IN ('PENDING', 'RETRY')
              AND next_attempt_at = :claimUntil
            """, nativeQuery = true)
    int markSent(
            @Param("outboxId") UUID outboxId,
            @Param("claimUntil") Instant claimUntil,
            @Param("sentAt") Instant sentAt
    );

    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'RETRY',
                attempt_count = :attemptCount,
                next_attempt_at = :nextAttemptAt,
                last_error_code = :errorCode
            WHERE id = :outboxId
              AND status IN ('PENDING', 'RETRY')
              AND next_attempt_at = :claimUntil
            """, nativeQuery = true)
    int markRetry(
            @Param("outboxId") UUID outboxId,
            @Param("claimUntil") Instant claimUntil,
            @Param("attemptCount") int attemptCount,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("errorCode") String errorCode
    );

    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'FAILED',
                attempt_count = :attemptCount,
                last_error_code = :errorCode
            WHERE id = :outboxId
              AND status IN ('PENDING', 'RETRY')
              AND next_attempt_at = :claimUntil
            """, nativeQuery = true)
    int markFailed(
            @Param("outboxId") UUID outboxId,
            @Param("claimUntil") Instant claimUntil,
            @Param("attemptCount") int attemptCount,
            @Param("errorCode") String errorCode
    );

    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'FAILED',
                attempt_count = :attemptCount,
                last_error_code = :errorCode
            WHERE id = :outboxId
              AND status IN ('PENDING', 'RETRY')
              AND next_attempt_at = :expectedNextAttemptAt
            """, nativeQuery = true)
    int markInvalidPayload(
            @Param("outboxId") UUID outboxId,
            @Param("expectedNextAttemptAt") Instant expectedNextAttemptAt,
            @Param("attemptCount") int attemptCount,
            @Param("errorCode") String errorCode
    );
}
