package com.priceradar.pricing.application;

import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;

import java.util.Optional;

public final class PriceSemanticsService {

    public InterpretedPrice interpret(ProviderPriceFields providerPriceFields) {
        if (providerPriceFields == null) {
            throw new IllegalArgumentException("providerPriceFields must not be null");
        }

        Optional<RubleAmount> productPrice = providerPriceFields.getProductPrice();
        Optional<RubleAmount> basicPrice = providerPriceFields.getBasicPrice();

        if (!providerPriceFields.isAvailable()) {
            return new InterpretedPrice(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    SnapshotStatus.UNAVAILABLE
            );
        }

        if (productPrice.isPresent()) {
            return new InterpretedPrice(
                    productPrice,
                    basicPrice,
                    Optional.of(PriceSource.PRODUCT),
                    SnapshotStatus.REGULAR_PRICE
            );
        }

        if (basicPrice.isPresent()) {
            return new InterpretedPrice(
                    Optional.empty(),
                    basicPrice,
                    Optional.of(PriceSource.BASIC_FALLBACK),
                    SnapshotStatus.BASIC_FALLBACK
            );
        }

        return new InterpretedPrice(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                SnapshotStatus.NO_PRICE
        );
    }
}
