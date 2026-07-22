package com.priceradar.statistics.application;

import java.time.Instant;
import java.util.UUID;

public interface PriceStatisticsStore {

    ObservedPriceStatistics calculate(
            UUID watchTargetId,
            Instant observedFromInclusive,
            Instant observedToInclusive
    );
}
