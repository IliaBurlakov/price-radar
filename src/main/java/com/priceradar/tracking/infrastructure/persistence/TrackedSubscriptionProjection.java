package com.priceradar.tracking.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public interface TrackedSubscriptionProjection {

    UUID getSubscriptionId();

    long getNmId();

    String getTitle();

    String getBrand();

    String getCanonicalUrl();

    String getVariantDisplayName();

    String getNotificationMode();

    Long getTargetPriceMinor();

    Instant getTrackingStartedAt();

    String getSnapshotStatus();

    String getPriceSource();

    Long getRegularPriceMinor();

    Instant getObservedAt();
}
