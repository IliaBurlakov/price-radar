package com.priceradar.notification.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryStore {

    List<PendingNotificationDelivery> findDue(Instant now, int limit);

    boolean claim(
            UUID outboxId,
            Instant expectedNextAttemptAt,
            Instant now,
            Instant claimUntil
    );

    boolean markSent(UUID outboxId, Instant claimUntil, Instant sentAt);

    boolean markRetry(
            UUID outboxId,
            Instant claimUntil,
            int attemptCount,
            Instant nextAttemptAt,
            String errorCode
    );

    boolean markFailed(
            UUID outboxId,
            Instant claimUntil,
            int attemptCount,
            String errorCode
    );
}
