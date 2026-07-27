package com.priceradar.scheduler.application;

import com.priceradar.notification.application.NotificationObservation;
import com.priceradar.tracking.application.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationFanOutService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationFanOutService.class);

    private final SubscriptionStore subscriptionStore;
    private final SubscriptionNotificationProcessor notificationProcessor;

    public NotificationFanOutService(
            SubscriptionStore subscriptionStore,
            SubscriptionNotificationProcessor notificationProcessor
    ) {
        if (subscriptionStore == null || notificationProcessor == null) {
            throw new IllegalArgumentException("notification fan-out dependencies must not be null");
        }
        this.subscriptionStore = subscriptionStore;
        this.notificationProcessor = notificationProcessor;
    }

    public void process(NotificationObservation observation, Instant completedAt) {
        if (observation == null || completedAt == null) {
            throw new IllegalArgumentException("notification fan-out fields must not be null");
        }
        List<UUID> subscriptionIds = subscriptionStore.findActiveIdsByWatchTargetId(
                observation.getWatchTargetId()
        );
        boolean failed = false;
        for (UUID subscriptionId : subscriptionIds) {
            try {
                notificationProcessor.process(subscriptionId, observation, completedAt);
            } catch (RuntimeException exception) {
                failed = true;
                LOGGER.error(
                        "Subscription notification processing failed, subscriptionId={}, snapshotId={}, errorType={}",
                        subscriptionId,
                        observation.getSnapshotId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
        if (failed) {
            throw new IllegalStateException("One or more subscription notifications failed");
        }
    }
}
