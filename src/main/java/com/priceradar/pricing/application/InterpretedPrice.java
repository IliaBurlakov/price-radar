package com.priceradar.pricing.application;

import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;

import java.util.Objects;
import java.util.Optional;

public final class InterpretedPrice {

    private final Optional<RubleAmount> regularPrice;
    private final Optional<RubleAmount> marketingBasePrice;
    private final Optional<PriceSource> priceSource;
    private final SnapshotStatus status;

    public InterpretedPrice(
            Optional<RubleAmount> regularPrice,
            Optional<RubleAmount> marketingBasePrice,
            Optional<PriceSource> priceSource,
            SnapshotStatus status
    ) {
        if (regularPrice == null) {
            throw new IllegalArgumentException("regularPrice must not be null");
        }
        if (marketingBasePrice == null) {
            throw new IllegalArgumentException("marketingBasePrice must not be null");
        }
        if (priceSource == null) {
            throw new IllegalArgumentException("priceSource must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        validateState(regularPrice, marketingBasePrice, priceSource, status);
        this.regularPrice = regularPrice;
        this.marketingBasePrice = marketingBasePrice;
        this.priceSource = priceSource;
        this.status = status;
    }

    private void validateState(
            Optional<RubleAmount> regularPrice,
            Optional<RubleAmount> marketingBasePrice,
            Optional<PriceSource> priceSource,
            SnapshotStatus status
    ) {
        if (status == SnapshotStatus.REGULAR_PRICE) {
            if (regularPrice.isEmpty()) {
                throw new IllegalArgumentException("REGULAR_PRICE status requires regularPrice");
            }
            if (priceSource.filter(PriceSource.PRODUCT::equals).isEmpty()) {
                throw new IllegalArgumentException("REGULAR_PRICE status requires PRODUCT priceSource");
            }
            if (regularPrice.orElseThrow().getMinorUnits() == 0
                    || marketingBasePrice.filter(value -> value.getMinorUnits() == 0).isPresent()) {
                throw new IllegalArgumentException("price observations must be positive");
            }
            return;
        }

        if (status == SnapshotStatus.BASIC_FALLBACK) {
            if (regularPrice.isPresent()) {
                throw new IllegalArgumentException("BASIC_FALLBACK status must not have regularPrice");
            }
            if (marketingBasePrice.isEmpty()) {
                throw new IllegalArgumentException("BASIC_FALLBACK status requires marketingBasePrice");
            }
            if (marketingBasePrice.orElseThrow().getMinorUnits() == 0) {
                throw new IllegalArgumentException("fallback price must be positive");
            }
            if (priceSource.filter(PriceSource.BASIC_FALLBACK::equals).isEmpty()) {
                throw new IllegalArgumentException("BASIC_FALLBACK status requires BASIC_FALLBACK priceSource");
            }
            return;
        }

        if (regularPrice.isPresent()) {
            throw new IllegalArgumentException(status + " status must not have regularPrice");
        }
        if (marketingBasePrice.isPresent()) {
            throw new IllegalArgumentException(status + " status must not have marketingBasePrice");
        }
        if (priceSource.isPresent()) {
            throw new IllegalArgumentException(status + " status must not have priceSource");
        }
    }

    public Optional<RubleAmount> getRegularPrice() {
        return regularPrice;
    }

    public Optional<RubleAmount> getMarketingBasePrice() {
        return marketingBasePrice;
    }

    public Optional<PriceSource> getPriceSource() {
        return priceSource;
    }

    public SnapshotStatus getStatus() {
        return status;
    }

    public boolean hasValidRegularPrice() {
        return status == SnapshotStatus.REGULAR_PRICE
                && priceSource.filter(PriceSource.PRODUCT::equals).isPresent()
                && regularPrice.isPresent();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InterpretedPrice that)) {
            return false;
        }
        return Objects.equals(regularPrice, that.regularPrice)
                && Objects.equals(marketingBasePrice, that.marketingBasePrice)
                && Objects.equals(priceSource, that.priceSource)
                && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(regularPrice, marketingBasePrice, priceSource, status);
    }

    @Override
    public String toString() {
        return "InterpretedPrice{" +
                "regularPrice=" + regularPrice +
                ", marketingBasePrice=" + marketingBasePrice +
                ", priceSource=" + priceSource +
                ", status=" + status +
                '}';
    }
}
