package com.priceradar.tracking.infrastructure.persistence;

import java.time.Instant;

public interface SubscriptionPriceHistoryProjection {

    Long getMinimumPriceMinor();

    Long getLatestPriceMinor();

    Instant getLatestObservedAt();
}
