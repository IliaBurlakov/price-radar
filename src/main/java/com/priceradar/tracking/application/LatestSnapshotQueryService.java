package com.priceradar.tracking.application;

import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public class LatestSnapshotQueryService {

    private final SubscriptionStore subscriptionStore;

    public LatestSnapshotQueryService(SubscriptionStore subscriptionStore) {
        if (subscriptionStore == null) {
            throw new IllegalArgumentException("subscriptionStore must not be null");
        }
        this.subscriptionStore = subscriptionStore;
    }

    @Transactional(readOnly = true)
    public Optional<LatestSnapshotView> findLatest(
            UUID userId,
            UUID subscriptionId
    ) {
        if (userId == null || subscriptionId == null) {
            throw new IllegalArgumentException("latest snapshot query fields must not be null");
        }
        return subscriptionStore.findLatestSnapshotActiveOwned(userId, subscriptionId);
    }
}
