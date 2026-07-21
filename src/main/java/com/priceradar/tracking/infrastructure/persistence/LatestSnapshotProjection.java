package com.priceradar.tracking.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public interface LatestSnapshotProjection {

    UUID getSubscriptionId();

    long getNmId();

    String getTitle();

    String getBrand();

    String getCanonicalUrl();

    String getVariantDisplayName();

    String getSnapshotStatus();

    String getPriceSource();

    Long getRegularPriceMinor();

    Long getMarketingBasePriceMinor();

    Instant getObservedAt();
}
