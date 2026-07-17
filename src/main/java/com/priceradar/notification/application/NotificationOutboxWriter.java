package com.priceradar.notification.application;

import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.SubscriptionStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public class NotificationOutboxWriter {

    private static final String IDEMPOTENCY_KEY_PREFIX = "scheduled:v1";

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
        if (!subscriptionStore.updateNotificationStateIfActive(decision.getSubscription())) {
            return NotificationOutboxWriteResult.SUBSCRIPTION_INACTIVE;
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

    private String idempotencyKey(NotificationIntent intent) {
        return IDEMPOTENCY_KEY_PREFIX
                + ":" + intent.getSubscriptionId()
                + ":" + intent.getType()
                + ":" + intent.getSnapshotId();
    }
}
