package com.priceradar.scheduler.application;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.pricing.application.InterpretedPrice;

import java.time.Instant;
import java.util.UUID;

public interface ScheduledObservationStore {

    UUID saveSnapshot(
            UUID checkId,
            UUID watchTargetId,
            InterpretedPrice price,
            Instant observedAt
    );

    void updateProductMetadata(
            UUID productId,
            MarketplaceProductDetails product,
            Instant observedAt
    );

    void markSuccessful(
            UUID watchTargetId,
            Instant completedAt,
            Instant nextCheckAt
    );

    void markFailed(
            UUID watchTargetId,
            Instant completedAt,
            Instant nextCheckAt
    );
}
