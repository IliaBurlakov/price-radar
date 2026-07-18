package com.priceradar.statistics.application;

import com.priceradar.pricing.domain.RubleAmount;

import java.math.BigDecimal;
import java.util.Optional;

public final class ObservedPriceStatistics {

    private final long observationCount;
    private final Optional<RubleAmount> minimumPrice;
    private final Optional<RubleAmount> maximumPrice;
    private final Optional<BigDecimal> averageMinorUnits;

    private ObservedPriceStatistics(
            long observationCount,
            Optional<RubleAmount> minimumPrice,
            Optional<RubleAmount> maximumPrice,
            Optional<BigDecimal> averageMinorUnits
    ) {
        validate(observationCount, minimumPrice, maximumPrice, averageMinorUnits);
        this.observationCount = observationCount;
        this.minimumPrice = minimumPrice;
        this.maximumPrice = maximumPrice;
        this.averageMinorUnits = averageMinorUnits;
    }

    public static ObservedPriceStatistics empty() {
        return new ObservedPriceStatistics(
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static ObservedPriceStatistics of(
            long observationCount,
            RubleAmount minimumPrice,
            RubleAmount maximumPrice,
            BigDecimal averageMinorUnits
    ) {
        return new ObservedPriceStatistics(
                observationCount,
                Optional.ofNullable(minimumPrice),
                Optional.ofNullable(maximumPrice),
                Optional.ofNullable(averageMinorUnits)
        );
    }

    public long getObservationCount() {
        return observationCount;
    }

    public Optional<RubleAmount> getMinimumPrice() {
        return minimumPrice;
    }

    public Optional<RubleAmount> getMaximumPrice() {
        return maximumPrice;
    }

    public Optional<BigDecimal> getAverageMinorUnits() {
        return averageMinorUnits;
    }

    private void validate(
            long observationCount,
            Optional<RubleAmount> minimumPrice,
            Optional<RubleAmount> maximumPrice,
            Optional<BigDecimal> averageMinorUnits
    ) {
        if (observationCount < 0 || minimumPrice == null || maximumPrice == null
                || averageMinorUnits == null) {
            throw new IllegalArgumentException("observed price statistics fields are invalid");
        }
        boolean hasValues = minimumPrice.isPresent()
                && maximumPrice.isPresent()
                && averageMinorUnits.isPresent();
        if ((observationCount == 0) == hasValues) {
            throw new IllegalArgumentException(
                    "statistics values must be present exactly when observations exist"
            );
        }
        if (!hasValues) {
            return;
        }
        long minimum = minimumPrice.orElseThrow().getMinorUnits();
        long maximum = maximumPrice.orElseThrow().getMinorUnits();
        BigDecimal average = averageMinorUnits.orElseThrow();
        if (minimum <= 0 || maximum < minimum
                || average.compareTo(BigDecimal.valueOf(minimum)) < 0
                || average.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw new IllegalArgumentException("observed price statistics values are inconsistent");
        }
    }
}
