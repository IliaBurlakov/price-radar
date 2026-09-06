package com.priceradar.scheduler.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationFanOutJobJpaRepository
        extends JpaRepository<NotificationFanOutJobEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO notification_fanout_jobs (
                snapshot_id, status, attempt_count, next_attempt_at, created_at
            ) VALUES (
                :snapshotId, 'PENDING', 0, :createdAt, :createdAt
            )
            ON CONFLICT (snapshot_id) DO NOTHING
            """, nativeQuery = true)
    int insertPending(
            @Param("snapshotId") UUID snapshotId,
            @Param("createdAt") Instant createdAt
    );

    @Query(value = """
            SELECT
                job.snapshot_id AS "snapshotId",
                snapshot.watch_target_id AS "watchTargetId",
                snapshot.status AS "snapshotStatus",
                snapshot.price_source AS "priceSource",
                snapshot.regular_price_minor AS "regularPriceMinor",
                snapshot.marketing_base_price_minor AS "marketingBasePriceMinor",
                snapshot.observed_at AS "observedAt",
                job.attempt_count AS "attemptCount",
                job.next_attempt_at AS "nextAttemptAt"
            FROM notification_fanout_jobs job
            JOIN price_snapshots snapshot ON snapshot.id = job.snapshot_id
            WHERE job.status IN ('PENDING', 'RETRY')
              AND job.next_attempt_at <= :now
              AND (job.claim_until IS NULL OR job.claim_until <= :now)
            ORDER BY job.next_attempt_at, job.snapshot_id
            LIMIT :limit
            """, nativeQuery = true)
    List<NotificationFanOutJobProjection> findDueJobs(
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    @Modifying
    @Query(value = """
            UPDATE notification_fanout_jobs
            SET claim_token = :claimToken,
                claim_until = :claimUntil
            WHERE snapshot_id = :snapshotId
              AND status IN ('PENDING', 'RETRY')
              AND next_attempt_at = :expectedNextAttemptAt
              AND next_attempt_at <= :now
              AND (claim_until IS NULL OR claim_until <= :now)
            """, nativeQuery = true)
    int claim(
            @Param("snapshotId") UUID snapshotId,
            @Param("expectedNextAttemptAt") Instant expectedNextAttemptAt,
            @Param("now") Instant now,
            @Param("claimToken") UUID claimToken,
            @Param("claimUntil") Instant claimUntil
    );

    @Modifying
    @Query(value = """
            UPDATE notification_fanout_jobs
            SET status = 'DONE',
                claim_token = NULL,
                claim_until = NULL,
                last_error_code = NULL,
                completed_at = :completedAt
            WHERE snapshot_id = :snapshotId
              AND status IN ('PENDING', 'RETRY')
              AND claim_token = :claimToken
            """, nativeQuery = true)
    int markDone(
            @Param("snapshotId") UUID snapshotId,
            @Param("claimToken") UUID claimToken,
            @Param("completedAt") Instant completedAt
    );

    @Modifying
    @Query(value = """
            UPDATE notification_fanout_jobs
            SET status = 'RETRY',
                attempt_count = :attemptCount,
                next_attempt_at = :nextAttemptAt,
                claim_token = NULL,
                claim_until = NULL,
                last_error_code = :errorCode
            WHERE snapshot_id = :snapshotId
              AND status IN ('PENDING', 'RETRY')
              AND claim_token = :claimToken
            """, nativeQuery = true)
    int markRetry(
            @Param("snapshotId") UUID snapshotId,
            @Param("claimToken") UUID claimToken,
            @Param("attemptCount") int attemptCount,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("errorCode") String errorCode
    );
}
