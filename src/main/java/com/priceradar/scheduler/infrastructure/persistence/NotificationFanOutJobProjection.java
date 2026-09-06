package com.priceradar.scheduler.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public interface NotificationFanOutJobProjection {

    UUID getSnapshotId();

    UUID getWatchTargetId();

    String getSnapshotStatus();

    String getPriceSource();

    Long getRegularPriceMinor();

    Long getMarketingBasePriceMinor();

    Instant getObservedAt();

    int getAttemptCount();

    Instant getNextAttemptAt();
}
