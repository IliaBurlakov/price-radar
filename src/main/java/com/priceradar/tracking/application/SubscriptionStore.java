package com.priceradar.tracking.application;

import com.priceradar.tracking.domain.Subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionStore {

    boolean watchTargetExists(UUID watchTargetId);

    Optional<Subscription> findActive(UUID userId, UUID watchTargetId);

    Optional<Subscription> findActiveOwned(UUID userId, UUID subscriptionId);

    Optional<Subscription> findActiveById(UUID subscriptionId);

    List<Subscription> findActiveByWatchTargetId(UUID watchTargetId);

    long countActive(UUID userId);

    List<TrackedSubscriptionItem> findActiveByUserId(UUID userId);

    Optional<LatestSnapshotView> findLatestSnapshotActiveOwned(
            UUID userId,
            UUID subscriptionId
    );

    Optional<SubscriptionQuoteObservation> findLatestQuoteObservation(UUID watchTargetId);

    Optional<SubscriptionQuoteObservation> findLatestRegularPriceObservation(UUID watchTargetId);

    Subscription create(Subscription subscription);

    NotificationStateUpdateResult updateNotificationStateIfActive(Subscription subscription);

    Subscription end(Subscription subscription);
}
