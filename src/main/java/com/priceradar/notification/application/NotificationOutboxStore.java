package com.priceradar.notification.application;

import java.time.Instant;

public interface NotificationOutboxStore {

    boolean enqueueIfAbsent(
            NotificationIntent intent,
            String idempotencyKey,
            Instant createdAt
    );
}
