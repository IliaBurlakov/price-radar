package com.priceradar.scheduler.application;

import com.priceradar.notification.application.NotificationObservation;

import java.time.Instant;

public final class PendingNotificationFanOutJob {

    private final NotificationObservation observation;
    private final int attemptCount;
    private final Instant nextAttemptAt;

    public PendingNotificationFanOutJob(
            NotificationObservation observation,
            int attemptCount,
            Instant nextAttemptAt
    ) {
        if (observation == null || nextAttemptAt == null) {
            throw new IllegalArgumentException("fan-out job fields must not be null");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("fan-out attempt count must not be negative");
        }
        this.observation = observation;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
    }

    public NotificationObservation getObservation() {
        return observation;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }
}
