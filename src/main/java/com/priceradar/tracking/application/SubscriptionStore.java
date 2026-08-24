package com.priceradar.tracking.application;

import com.priceradar.tracking.domain.Subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionStore {

    Optional<Subscription> findActive(UUID userId, UUID watchTargetId);

    Optional<Subscription> findActiveOwned(UUID userId, UUID subscriptionId);

    Optional<Subscription> findActiveById(UUID subscriptionId);

    List<UUID> findActiveIdsByWatchTargetId(UUID watchTargetId);

    long countActive(UUID userId);

    List<TrackedSubscriptionItem> findActiveByUserId(UUID userId);

    List<Subscription> findActiveSubscriptions(UUID userId);

    Optional<LatestSnapshotView> findLatestSnapshotActiveOwned(
            UUID userId,
            UUID subscriptionId
    );

    Optional<SubscriptionQuoteObservation> findQuoteObservation(UUID snapshotId);

    Subscription create(Subscription subscription);

    NotificationStateUpdateResult updateNotificationStateIfActive(Subscription subscription);

    Subscription end(Subscription subscription);
}
