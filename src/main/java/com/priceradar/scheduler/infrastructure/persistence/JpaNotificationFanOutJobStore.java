package com.priceradar.scheduler.infrastructure.persistence;

import com.priceradar.notification.application.NotificationObservation;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.scheduler.application.NotificationFanOutJobStore;
import com.priceradar.scheduler.application.PendingNotificationFanOutJob;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaNotificationFanOutJobStore implements NotificationFanOutJobStore {

    private final NotificationFanOutJobJpaRepository repository;

    public JpaNotificationFanOutJobStore(NotificationFanOutJobJpaRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("fan-out job repository must not be null");
        }
        this.repository = repository;
    }

    @Override
    @Transactional
    public void createIfAbsent(UUID snapshotId, Instant createdAt) {
        if (snapshotId == null || createdAt == null) {
            throw new IllegalArgumentException("fan-out job identity must not be null");
        }
        repository.insertPending(snapshotId, createdAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingNotificationFanOutJob> findDue(Instant now, int limit) {
        if (now == null || limit <= 0) {
            throw new IllegalArgumentException("fan-out job query fields are invalid");
        }
        return repository.findDueJobs(now, limit).stream()
                .map(this::toPendingJob)
                .toList();
    }

    @Override
    @Transactional
    public boolean claim(
            UUID snapshotId,
            Instant expectedNextAttemptAt,
            Instant now,
            UUID claimToken,
            Instant claimUntil
    ) {
        if (snapshotId == null || expectedNextAttemptAt == null || now == null
                || claimToken == null || claimUntil == null) {
            throw new IllegalArgumentException("fan-out claim fields must not be null");
        }
        if (!claimUntil.isAfter(now)) {
            throw new IllegalArgumentException("fan-out claimUntil must be after now");
        }
        return repository.claim(
                snapshotId,
                expectedNextAttemptAt,
                now,
                claimToken,
                claimUntil
        ) == 1;
    }

    @Override
    @Transactional
    public boolean markDone(UUID snapshotId, UUID claimToken, Instant completedAt) {
        if (snapshotId == null || claimToken == null || completedAt == null) {
            throw new IllegalArgumentException("fan-out completion fields must not be null");
        }
        return repository.markDone(snapshotId, claimToken, completedAt) == 1;
    }

    @Override
    @Transactional
    public boolean markRetry(
            UUID snapshotId,
            UUID claimToken,
            int attemptCount,
            Instant nextAttemptAt,
            String errorCode
    ) {
        if (snapshotId == null || claimToken == null || nextAttemptAt == null
                || errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("fan-out retry fields must not be null or blank");
        }
        if (attemptCount <= 0 || errorCode.length() > 64) {
            throw new IllegalArgumentException("fan-out retry values are invalid");
        }
        return repository.markRetry(
                snapshotId,
                claimToken,
                attemptCount,
                nextAttemptAt,
                errorCode
        ) == 1;
    }

    private PendingNotificationFanOutJob toPendingJob(
            NotificationFanOutJobProjection projection
    ) {
        SnapshotStatus status = SnapshotStatus.valueOf(projection.getSnapshotStatus());
        InterpretedPrice price = new InterpretedPrice(
                Optional.ofNullable(projection.getRegularPriceMinor())
                        .map(RubleAmount::ofMinorUnits),
                Optional.ofNullable(projection.getMarketingBasePriceMinor())
                        .map(RubleAmount::ofMinorUnits),
                Optional.ofNullable(projection.getPriceSource()).map(PriceSource::valueOf),
                status
        );
        NotificationObservation observation = new NotificationObservation(
                projection.getSnapshotId(),
                projection.getWatchTargetId(),
                price,
                projection.getObservedAt()
        );
        return new PendingNotificationFanOutJob(
                observation,
                projection.getAttemptCount(),
                projection.getNextAttemptAt()
        );
    }
}
