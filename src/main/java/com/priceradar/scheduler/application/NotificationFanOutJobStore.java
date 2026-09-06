package com.priceradar.scheduler.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationFanOutJobStore {

    void createIfAbsent(UUID snapshotId, Instant createdAt);

    List<PendingNotificationFanOutJob> findDue(Instant now, int limit);

    boolean claim(
            UUID snapshotId,
            Instant expectedNextAttemptAt,
            Instant now,
            UUID claimToken,
            Instant claimUntil
    );

    boolean markDone(UUID snapshotId, UUID claimToken, Instant completedAt);

    boolean markRetry(
            UUID snapshotId,
            UUID claimToken,
            int attemptCount,
            Instant nextAttemptAt,
            String errorCode
    );
}
