package com.priceradar.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
