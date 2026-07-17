package com.priceradar.tracking.application;

import com.priceradar.tracking.domain.Subscription;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionStore {

    boolean watchTargetExists(UUID watchTargetId);

    Optional<Subscription> findActive(UUID userId, UUID watchTargetId);

    Optional<Subscription> findActiveOwned(UUID userId, UUID subscriptionId);

    long countActive(UUID userId);

    Optional<SubscriptionQuoteObservation> findLatestQuoteObservation(UUID watchTargetId);

    Subscription create(Subscription subscription);

    Subscription end(Subscription subscription);
}
