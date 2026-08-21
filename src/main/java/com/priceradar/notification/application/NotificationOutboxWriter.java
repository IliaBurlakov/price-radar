package com.priceradar.notification.application;

import com.priceradar.notification.domain.NotificationType;
import com.priceradar.tracking.application.InitialThresholdNotificationEnqueuer;
import com.priceradar.tracking.application.NotificationStateUpdateResult;
import com.priceradar.tracking.application.SubscriptionQuoteObservation;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public class NotificationOutboxWriter implements InitialThresholdNotificationEnqueuer {

    private static final String IDEMPOTENCY_KEY_PREFIX = "scheduled:v1";
    private static final String INITIAL_THRESHOLD_KEY_PREFIX = "subscription-created:v1";

    private final SubscriptionStore subscriptionStore;
    private final NotificationOutboxStore outboxStore;

    public NotificationOutboxWriter(
            SubscriptionStore subscriptionStore,
            NotificationOutboxStore outboxStore
    ) {
        if (subscriptionStore == null || outboxStore == null) {
            throw new IllegalArgumentException("notification outbox writer dependencies must not be null");
        }
        this.subscriptionStore = subscriptionStore;
        this.outboxStore = outboxStore;
    }

    @Transactional
    public NotificationOutboxWriteResult persist(
            NotificationDecisionResult decision,
            Instant createdAt
    ) {
        if (decision == null || createdAt == null) {
            throw new IllegalArgumentException("notification persistence fields must not be null");
        }
        if (!decision.isStateChanged()) {
            return NotificationOutboxWriteResult.NO_STATE_CHANGE;
        }
        if (decision.getSubscription().getStatus() != SubscriptionStatus.ACTIVE) {
            return NotificationOutboxWriteResult.SUBSCRIPTION_INACTIVE;
        }
        Optional<NotificationIntent> notificationIntent = decision.getNotificationIntent();
        if (notificationIntent
                .filter(intent -> createdAt.isBefore(intent.getObservedAt()))
                .isPresent()) {
            throw new IllegalArgumentException("outbox creation time must not precede observation");
        }
        NotificationStateUpdateResult updateResult = subscriptionStore
                .updateNotificationStateIfActive(decision.getSubscription());
        if (updateResult == NotificationStateUpdateResult.CONFLICT) {
            return NotificationOutboxWriteResult.STATE_CONFLICT;
        }
        if (notificationIntent.isEmpty()) {
            return NotificationOutboxWriteResult.STATE_UPDATED;
        }

        NotificationIntent intent = notificationIntent.orElseThrow();
        boolean inserted = outboxStore.enqueueIfAbsent(
                intent,
                idempotencyKey(intent),
                createdAt
        );
        return inserted
                ? NotificationOutboxWriteResult.NOTIFICATION_ENQUEUED
                : NotificationOutboxWriteResult.NOTIFICATION_ALREADY_ENQUEUED;
    }

    @Override
    @Transactional
    public void enqueue(
            Subscription subscription,
            SubscriptionQuoteObservation observation,
            Instant createdAt
    ) {
        validateInitialThresholdNotification(subscription, observation, createdAt);
        NotificationIntent intent = new NotificationIntent(
                subscription.getId(),
                observation.getSnapshotId(),
                NotificationType.TARGET_REACHED,
                Optional.empty(),
                observation.getRegularPrice().orElseThrow(),
                observation.getObservedAt()
        );
        outboxStore.enqueueIfAbsent(
                intent,
                INITIAL_THRESHOLD_KEY_PREFIX
                        + ":" + subscription.getId()
                        + ":" + observation.getSnapshotId(),
                createdAt
        );
    }

    private String idempotencyKey(NotificationIntent intent) {
        return IDEMPOTENCY_KEY_PREFIX
                + ":" + intent.getSubscriptionId()
                + ":" + intent.getType()
                + ":" + intent.getSnapshotId();
    }

    private void validateInitialThresholdNotification(
            Subscription subscription,
            SubscriptionQuoteObservation observation,
            Instant createdAt
    ) {
        if (subscription == null || observation == null || createdAt == null) {
            throw new IllegalArgumentException("initial threshold notification fields must not be null");
        }
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                || subscription.getNotificationMode() != NotificationMode.TARGET_PRICE
                || subscription.getThresholdState() != ThresholdState.REACHED_NOTIFIED) {
            throw new IllegalArgumentException("initial threshold notification requires an active reached subscription");
        }
        if (!subscription.getWatchTargetId().equals(observation.getWatchTargetId())) {
            throw new IllegalArgumentException("initial threshold observation belongs to another watch target");
        }
        long currentPrice = observation.getRegularPrice()
                .orElseThrow(() -> new IllegalArgumentException("initial threshold notification requires regular price"))
                .getMinorUnits();
        if (currentPrice > subscription.getTargetPrice().orElseThrow().getMinorUnits()) {
            throw new IllegalArgumentException("initial threshold price must be at or below target");
        }
        if (createdAt.isBefore(observation.getObservedAt())) {
            throw new IllegalArgumentException("outbox creation time must not precede observation");
        }
    }
}
