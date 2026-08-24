package com.priceradar.statistics.application;

import com.priceradar.pricing.domain.RubleAmount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

public final class ObservedPriceStatistics {

    private final long observationCount;
    private final Optional<RubleAmount> minimumPrice;
    private final Optional<Instant> minimumObservedAt;
    private final Optional<RubleAmount> maximumPrice;
    private final Optional<BigDecimal> averageMinorUnits;
    private final Optional<RubleAmount> firstPrice;
    private final Optional<RubleAmount> latestPrice;

    private ObservedPriceStatistics(
            long observationCount,
            Optional<RubleAmount> minimumPrice,
            Optional<Instant> minimumObservedAt,
            Optional<RubleAmount> maximumPrice,
            Optional<BigDecimal> averageMinorUnits,
            Optional<RubleAmount> firstPrice,
            Optional<RubleAmount> latestPrice
    ) {
        validate(
                observationCount,
                minimumPrice,
                minimumObservedAt,
                maximumPrice,
                averageMinorUnits,
                firstPrice,
                latestPrice
        );
        this.observationCount = observationCount;
        this.minimumPrice = minimumPrice;
        this.minimumObservedAt = minimumObservedAt;
        this.maximumPrice = maximumPrice;
        this.averageMinorUnits = averageMinorUnits;
        this.firstPrice = firstPrice;
        this.latestPrice = latestPrice;
    }

    public static ObservedPriceStatistics empty() {
        return new ObservedPriceStatistics(
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static ObservedPriceStatistics of(
            long observationCount,
            RubleAmount minimumPrice,
            Instant minimumObservedAt,
            RubleAmount maximumPrice,
            BigDecimal averageMinorUnits,
            RubleAmount firstPrice,
            RubleAmount latestPrice
    ) {
        return new ObservedPriceStatistics(
                observationCount,
                Optional.ofNullable(minimumPrice),
                Optional.ofNullable(minimumObservedAt),
                Optional.ofNullable(maximumPrice),
                Optional.ofNullable(averageMinorUnits),
                Optional.ofNullable(firstPrice),
                Optional.ofNullable(latestPrice)
        );
    }

    public long getObservationCount() {
        return observationCount;
    }

    public Optional<RubleAmount> getMinimumPrice() {
        return minimumPrice;
    }

    public Optional<Instant> getMinimumObservedAt() {
        return minimumObservedAt;
    }

    public Optional<RubleAmount> getMaximumPrice() {
        return maximumPrice;
    }

    public Optional<BigDecimal> getAverageMinorUnits() {
        return averageMinorUnits;
    }

    public Optional<RubleAmount> getFirstPrice() {
        return firstPrice;
    }

    public Optional<RubleAmount> getLatestPrice() {
        return latestPrice;
    }

    public Optional<Long> getPriceChangeMinorUnits() {
        if (firstPrice.isEmpty() || latestPrice.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                latestPrice.orElseThrow().getMinorUnits()
                        - firstPrice.orElseThrow().getMinorUnits()
        );
    }

    public Optional<BigDecimal> getPriceChangePercent() {
        if (firstPrice.isEmpty() || latestPrice.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal first = BigDecimal.valueOf(firstPrice.orElseThrow().getMinorUnits());
        return Optional.of(BigDecimal.valueOf(getPriceChangeMinorUnits().orElseThrow())
                .multiply(BigDecimal.valueOf(100))
                .divide(first, 2, RoundingMode.HALF_UP));
    }

    public Optional<RubleAmount> getLatestPriceDifferenceFromMinimum() {
        if (latestPrice.isEmpty() || minimumPrice.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(RubleAmount.ofMinorUnits(
                latestPrice.orElseThrow().getMinorUnits()
                        - minimumPrice.orElseThrow().getMinorUnits()
        ));
    }

    private void validate(
            long observationCount,
            Optional<RubleAmount> minimumPrice,
            Optional<Instant> minimumObservedAt,
            Optional<RubleAmount> maximumPrice,
            Optional<BigDecimal> averageMinorUnits,
            Optional<RubleAmount> firstPrice,
            Optional<RubleAmount> latestPrice
    ) {
        if (observationCount < 0 || minimumPrice == null || maximumPrice == null
                || minimumObservedAt == null || averageMinorUnits == null
                || firstPrice == null || latestPrice == null) {
            throw new IllegalArgumentException("observed price statistics fields are invalid");
        }
        boolean hasValues = minimumPrice.isPresent()
                && minimumObservedAt.isPresent()
                && maximumPrice.isPresent()
                && averageMinorUnits.isPresent()
                && firstPrice.isPresent()
                && latestPrice.isPresent();
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
        long first = firstPrice.orElseThrow().getMinorUnits();
        long latest = latestPrice.orElseThrow().getMinorUnits();
        BigDecimal average = averageMinorUnits.orElseThrow();
        if (minimum <= 0 || maximum < minimum
                || first < minimum || first > maximum
                || latest < minimum || latest > maximum
                || average.compareTo(BigDecimal.valueOf(minimum)) < 0
                || average.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw new IllegalArgumentException("observed price statistics values are inconsistent");
        }
    }
}
