package com.priceradar.tracking.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public interface DueWatchTargetProjection {

    UUID getWatchTargetId();

    UUID getProductId();

    String getMarketplace();

    long getExternalProductId();

    String getVariantKind();

    String getVariantValue();

    long getDest();

    int getSpp();

    Instant getNextCheckAt();
}
