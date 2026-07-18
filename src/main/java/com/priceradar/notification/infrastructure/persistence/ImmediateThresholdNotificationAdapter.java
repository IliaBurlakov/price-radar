package com.priceradar.notification.infrastructure.persistence;

import com.priceradar.notification.application.NotificationIntent;
import com.priceradar.notification.application.NotificationOutboxStore;
import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.tracking.application.ImmediateThresholdNotificationPort;
import com.priceradar.tracking.application.SubscriptionQuoteObservation;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.ThresholdState;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class ImmediateThresholdNotificationAdapter
        implements ImmediateThresholdNotificationPort {

    private static final String IDEMPOTENCY_KEY_PREFIX = "creation:v1";

    private final NotificationOutboxStore outboxStore;

    public ImmediateThresholdNotificationAdapter(NotificationOutboxStore outboxStore) {
        if (outboxStore == null) {
            throw new IllegalArgumentException("outboxStore must not be null");
        }
        this.outboxStore = outboxStore;
    }

    @Override
    public void enqueue(
            Subscription subscription,
            SubscriptionQuoteObservation observation,
            Instant createdAt
    ) {
        validate(subscription, observation, createdAt);
        RubleAmount currentPrice = observation.getRegularPrice().orElseThrow();
        NotificationIntent intent = new NotificationIntent(
                subscription.getId(),
                observation.getSnapshotId(),
                NotificationType.TARGET_REACHED,
                Optional.empty(),
                currentPrice,
                observation.getObservedAt()
        );
        outboxStore.enqueueIfAbsent(
                intent,
                IDEMPOTENCY_KEY_PREFIX + ":" + subscription.getId(),
                createdAt
        );
    }

    private void validate(
            Subscription subscription,
            SubscriptionQuoteObservation observation,
            Instant createdAt
    ) {
        if (subscription == null || observation == null || createdAt == null) {
            throw new IllegalArgumentException("immediate threshold notification fields must not be null");
        }
        if (subscription.getNotificationMode() != NotificationMode.TARGET_PRICE
                || subscription.getThresholdState() != ThresholdState.REACHED_NOTIFIED) {
            throw new IllegalArgumentException("already-reached target subscription is required");
        }
        RubleAmount currentPrice = observation.getRegularPrice().orElseThrow(() ->
                new IllegalArgumentException("regular price observation is required"));
        if (currentPrice.getMinorUnits()
                > subscription.getTargetPrice().orElseThrow().getMinorUnits()) {
            throw new IllegalArgumentException("current price must have reached target");
        }
        if (createdAt.isBefore(observation.getObservedAt())) {
            throw new IllegalArgumentException("outbox creation time must not precede observation");
        }
    }
}
