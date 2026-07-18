package com.priceradar.scheduler.application;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.notification.application.NotificationDecisionResult;
import com.priceradar.notification.application.NotificationDecisionService;
import com.priceradar.notification.application.NotificationObservation;
import com.priceradar.notification.application.NotificationOutboxWriteResult;
import com.priceradar.notification.application.NotificationOutboxWriter;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.Subscription;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WatchTargetCheckTransaction {

    private static final int MAX_STATE_REEVALUATIONS = 3;

    private final ScheduledObservationStore observationStore;
    private final SubscriptionStore subscriptionStore;
    private final NotificationDecisionService decisionService;
    private final NotificationOutboxWriter outboxWriter;

    public WatchTargetCheckTransaction(
            ScheduledObservationStore observationStore,
            SubscriptionStore subscriptionStore,
            NotificationDecisionService decisionService,
            NotificationOutboxWriter outboxWriter
    ) {
        if (observationStore == null || subscriptionStore == null
                || decisionService == null || outboxWriter == null) {
            throw new IllegalArgumentException("watch target check dependencies must not be null");
        }
        this.observationStore = observationStore;
        this.subscriptionStore = subscriptionStore;
        this.decisionService = decisionService;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void persistObservation(
            DueWatchTarget target,
            UUID checkId,
            MarketplaceProductDetails product,
            InterpretedPrice price,
            Instant observedAt,
            Instant completedAt,
            Instant nextCheckAt
    ) {
        validateObservation(
                target,
                checkId,
                product,
                price,
                observedAt,
                completedAt,
                nextCheckAt
        );
        UUID snapshotId = observationStore.saveSnapshot(
                checkId,
                target.getWatchTargetId(),
                price,
                observedAt
        );
        observationStore.updateProductMetadata(target.getProductId(), product, observedAt);

        NotificationObservation observation = new NotificationObservation(
                snapshotId,
                target.getWatchTargetId(),
                price,
                observedAt
        );
        List<Subscription> subscriptions = subscriptionStore.findActiveByWatchTargetId(
                target.getWatchTargetId()
        );
        for (Subscription subscription : subscriptions) {
            if (!observedAt.isBefore(subscription.getCreatedAt())) {
                applyObservation(subscription, observation, completedAt);
            }
        }
        observationStore.markSuccessful(
                target.getWatchTargetId(),
                completedAt,
                nextCheckAt
        );
    }

    @Transactional
    public void persistFailure(
            DueWatchTarget target,
            Instant completedAt,
            Instant nextCheckAt
    ) {
        if (target == null || completedAt == null || nextCheckAt == null) {
            throw new IllegalArgumentException("failed check fields must not be null");
        }
        if (!nextCheckAt.isAfter(completedAt)) {
            throw new IllegalArgumentException("failed check must be rescheduled in the future");
        }
        observationStore.markFailed(target.getWatchTargetId(), completedAt, nextCheckAt);
    }

    private void applyObservation(
            Subscription initialSubscription,
            NotificationObservation observation,
            Instant completedAt
    ) {
        Subscription current = initialSubscription;
        for (int attempt = 0; attempt < MAX_STATE_REEVALUATIONS; attempt++) {
            NotificationDecisionResult decision = decisionService.evaluate(current, observation);
            NotificationOutboxWriteResult result = outboxWriter.persist(decision, completedAt);
            if (result != NotificationOutboxWriteResult.STATE_CONFLICT) {
                return;
            }
            Optional<Subscription> reloaded = subscriptionStore.findActiveById(current.getId());
            if (reloaded.isEmpty()) {
                return;
            }
            current = reloaded.orElseThrow();
        }
        throw new IllegalStateException(
                "Could not apply notification state after concurrent updates"
        );
    }

    private void validateObservation(
            DueWatchTarget target,
            UUID checkId,
            MarketplaceProductDetails product,
            InterpretedPrice price,
            Instant observedAt,
            Instant completedAt,
            Instant nextCheckAt
    ) {
        if (target == null || checkId == null || product == null || price == null
                || observedAt == null || completedAt == null || nextCheckAt == null) {
            throw new IllegalArgumentException("scheduled observation fields must not be null");
        }
        if (observedAt.isAfter(completedAt)) {
            throw new IllegalArgumentException("observation time must not be after completion time");
        }
        if (!nextCheckAt.isAfter(completedAt)) {
            throw new IllegalArgumentException("successful check must be rescheduled in the future");
        }
    }
}
