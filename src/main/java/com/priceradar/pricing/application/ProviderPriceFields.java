package com.priceradar.pricing.application;

import com.priceradar.pricing.domain.RubleAmount;

import java.util.Objects;
import java.util.Optional;

public final class ProviderPriceFields {

    private final boolean available;
    private final Optional<RubleAmount> productPrice;
    private final Optional<RubleAmount> basicPrice;

    public ProviderPriceFields(
            boolean available,
            Optional<RubleAmount> productPrice,
            Optional<RubleAmount> basicPrice
    ) {
        if (productPrice == null) {
            throw new IllegalArgumentException("productPrice must not be null");
        }
        if (basicPrice == null) {
            throw new IllegalArgumentException("basicPrice must not be null");
        }
        this.available = available;
        this.basicPrice = basicPrice;
        this.productPrice = productPrice;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public Optional<RubleAmount> getProductPrice() {
        return this.productPrice;
    }

    public Optional<RubleAmount> getBasicPrice() {
        return this.basicPrice;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProviderPriceFields that)) {
            return false;
        }
        return available == that.available
                && Objects.equals(productPrice, that.productPrice)
                && Objects.equals(basicPrice, that.basicPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(available, productPrice, basicPrice);
    }

    @Override
    public String toString() {
        return "ProviderPriceFields{" +
                "available=" + available +
                ", productPrice=" + productPrice +
                ", basicPrice=" + basicPrice +
                '}';
    }
}
