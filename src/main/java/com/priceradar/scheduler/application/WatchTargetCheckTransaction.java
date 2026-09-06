package com.priceradar.scheduler.application;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.notification.application.NotificationObservation;
import com.priceradar.pricing.application.InterpretedPrice;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public class WatchTargetCheckTransaction {

    private final ScheduledObservationStore observationStore;
    private final NotificationFanOutJobStore fanOutJobStore;

    public WatchTargetCheckTransaction(
            ScheduledObservationStore observationStore,
            NotificationFanOutJobStore fanOutJobStore
    ) {
        if (observationStore == null || fanOutJobStore == null) {
            throw new IllegalArgumentException("watch target check dependencies must not be null");
        }
        this.observationStore = observationStore;
        this.fanOutJobStore = fanOutJobStore;
    }

    @Transactional
    public NotificationObservation persistObservation(
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
        fanOutJobStore.createIfAbsent(snapshotId, completedAt);
        observationStore.updateProductMetadata(target.getProductId(), product, observedAt);

        NotificationObservation observation = new NotificationObservation(
                snapshotId,
                target.getWatchTargetId(),
                price,
                observedAt
        );
        observationStore.markSuccessful(
                target.getWatchTargetId(),
                completedAt,
                nextCheckAt
        );
        return observation;
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
