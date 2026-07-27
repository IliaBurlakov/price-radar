package com.priceradar.scheduler.application;

import com.priceradar.notification.application.NotificationDecisionResult;
import com.priceradar.notification.application.NotificationDecisionService;
import com.priceradar.notification.application.NotificationObservation;
import com.priceradar.notification.application.NotificationOutboxWriteResult;
import com.priceradar.notification.application.NotificationOutboxWriter;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.Subscription;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class SubscriptionNotificationProcessor {

    private static final int MAX_STATE_REEVALUATIONS = 3;

    private final SubscriptionStore subscriptionStore;
    private final NotificationDecisionService decisionService;
    private final NotificationOutboxWriter outboxWriter;

    public SubscriptionNotificationProcessor(
            SubscriptionStore subscriptionStore,
            NotificationDecisionService decisionService,
            NotificationOutboxWriter outboxWriter
    ) {
        if (subscriptionStore == null || decisionService == null || outboxWriter == null) {
            throw new IllegalArgumentException("subscription notification dependencies must not be null");
        }
        this.subscriptionStore = subscriptionStore;
        this.decisionService = decisionService;
        this.outboxWriter = outboxWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(
            UUID subscriptionId,
            NotificationObservation observation,
            Instant completedAt
    ) {
        if (subscriptionId == null || observation == null || completedAt == null) {
            throw new IllegalArgumentException("subscription observation fields must not be null");
        }
        Optional<Subscription> active = subscriptionStore.findActiveById(subscriptionId);
        if (active.isEmpty() || observation.getObservedAt().isBefore(active.get().getCreatedAt())) {
            return;
        }

        Subscription current = active.orElseThrow();
        for (int attempt = 0; attempt < MAX_STATE_REEVALUATIONS; attempt++) {
            NotificationDecisionResult decision = decisionService.evaluate(current, observation);
            NotificationOutboxWriteResult result = outboxWriter.persist(decision, completedAt);
            if (result != NotificationOutboxWriteResult.STATE_CONFLICT) {
                return;
            }
            Optional<Subscription> reloaded = subscriptionStore.findActiveById(subscriptionId);
            if (reloaded.isEmpty()) {
                return;
            }
            current = reloaded.orElseThrow();
        }
        throw new IllegalStateException(
                "Could not apply notification state after concurrent updates"
        );
    }
}
