package com.priceradar.scheduler.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationFanOutJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationFanOutJobService.class);
    private static final String PROCESSING_FAILED = "PROCESSING_FAILED";

    private final NotificationFanOutJobStore jobStore;
    private final NotificationFanOutService fanOutService;
    private final Clock clock;
    private final int batchSize;
    private final Duration claimTimeout;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public NotificationFanOutJobService(
            NotificationFanOutJobStore jobStore,
            NotificationFanOutService fanOutService,
            Clock clock,
            int batchSize,
            Duration claimTimeout,
            Duration baseBackoff,
            Duration maxBackoff
    ) {
        if (jobStore == null || fanOutService == null || clock == null
                || claimTimeout == null || baseBackoff == null || maxBackoff == null) {
            throw new IllegalArgumentException("fan-out job dependencies must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("fan-out batch size must be positive");
        }
        validatePositive(claimTimeout, "claimTimeout");
        validatePositive(baseBackoff, "baseBackoff");
        validatePositive(maxBackoff, "maxBackoff");
        if (baseBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException("fan-out baseBackoff must not exceed maxBackoff");
        }
        this.jobStore = jobStore;
        this.fanOutService = fanOutService;
        this.clock = clock;
        this.batchSize = batchSize;
        this.claimTimeout = claimTimeout;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
    }

    public void processDue() {
        Instant batchStartedAt = clock.instant();
        List<PendingNotificationFanOutJob> jobs = jobStore.findDue(batchStartedAt, batchSize);
        for (PendingNotificationFanOutJob job : jobs) {
            process(job);
        }
    }

    private void process(PendingNotificationFanOutJob job) {
        Instant claimedAt = clock.instant();
        Instant claimUntil = claimedAt.plus(claimTimeout);
        UUID claimToken = UUID.randomUUID();
        UUID snapshotId = job.getObservation().getSnapshotId();
        if (!jobStore.claim(
                snapshotId,
                job.getNextAttemptAt(),
                claimedAt,
                claimToken,
                claimUntil
        )) {
            return;
        }

        try {
            fanOutService.process(job.getObservation(), clock.instant());
        } catch (RuntimeException exception) {
            scheduleRetry(job, claimToken, exception);
            return;
        }

        if (!jobStore.markDone(snapshotId, claimToken, clock.instant())) {
            LOGGER.warn(
                    "Notification fan-out claim changed before completion, snapshotId={}",
                    snapshotId
            );
            return;
        }
        LOGGER.info(
                "Notification fan-out completed, snapshotId={}, attemptsBeforeSuccess={}",
                snapshotId,
                job.getAttemptCount()
        );
    }

    private void scheduleRetry(
            PendingNotificationFanOutJob job,
            UUID claimToken,
            RuntimeException exception
    ) {
        int attemptCount = job.getAttemptCount() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : job.getAttemptCount() + 1;
        Instant nextAttemptAt = clock.instant().plus(backoff(attemptCount));
        UUID snapshotId = job.getObservation().getSnapshotId();
        boolean updated = jobStore.markRetry(
                snapshotId,
                claimToken,
                attemptCount,
                nextAttemptAt,
                PROCESSING_FAILED
        );
        if (!updated) {
            LOGGER.warn(
                    "Notification fan-out claim changed before retry, snapshotId={}",
                    snapshotId
            );
            return;
        }
        LOGGER.error(
                "Notification fan-out retry scheduled, snapshotId={}, attempt={}, nextAttemptAt={}, errorType={}",
                snapshotId,
                attemptCount,
                nextAttemptAt,
                exception.getClass().getSimpleName()
        );
    }

    private Duration backoff(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount - 1, 0), 30);
        long multiplier = 1L << exponent;
        Duration calculated;
        try {
            calculated = baseBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return maxBackoff;
        }
        return calculated.compareTo(maxBackoff) > 0 ? maxBackoff : calculated;
    }

    private void validatePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
