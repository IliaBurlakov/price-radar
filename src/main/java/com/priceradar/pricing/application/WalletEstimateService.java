package com.priceradar.pricing.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.WalletPriceSource;
import com.priceradar.user.domain.UserPricePreferences;

import java.util.Optional;

public final class WalletEstimateService {

    public Optional<WalletEstimate> estimate(InterpretedPrice interpretedPrice, UserPricePreferences preferences) {
        if (interpretedPrice == null) {
            throw new IllegalArgumentException("interpretedPrice must not be null");
        }
        if (preferences == null) {
            throw new IllegalArgumentException("preferences must not be null");
        }
        if (!interpretedPrice.hasValidRegularPrice()) {
            return Optional.empty();
        }

        return estimateFromRegularPrice(
                interpretedPrice.getRegularPrice().orElseThrow(),
                preferences
        );
    }

    public Optional<WalletEstimate> estimateFromRegularPrice(
            RubleAmount regularPrice,
            UserPricePreferences preferences
    ) {
        if (regularPrice == null || preferences == null) {
            throw new IllegalArgumentException("wallet estimate fields must not be null");
        }
        if (regularPrice.getMinorUnits() == 0) {
            return Optional.empty();
        }

        long regularPriceMinorUnits = regularPrice.getMinorUnits();
        int discountPercent = preferences.getWalletDiscountPercent();
        long discountedMinorUnits = floorPercent(
                regularPriceMinorUnits,
                100 - discountPercent
        );
        long walletPriceMinorUnits = floorToWholeRubles(discountedMinorUnits);

        return Optional.of(new WalletEstimate(
                RubleAmount.ofMinorUnits(walletPriceMinorUnits),
                discountPercent,
                WalletPriceSource.ESTIMATED_BY_PERCENT
        ));
    }

    private long floorPercent(long minorUnits, int percent) {
        long wholeHundreds = minorUnits / 100;
        long remainder = minorUnits % 100;
        return wholeHundreds * percent + (remainder * percent) / 100;
    }

    private long floorToWholeRubles(long minorUnits) {
        return (minorUnits / 100) * 100;
    }
}
