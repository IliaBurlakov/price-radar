package com.priceradar.pricing.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.WalletPriceSource;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PricingRulesTest {

    @Test
    void calculatesWalletEstimateByFlooringTheConfiguredPercentage() {
        InterpretedPrice price = new InterpretedPrice(
                Optional.of(RubleAmount.ofMinorUnits(118_534L)),
                Optional.empty(),
                Optional.of(com.priceradar.pricing.domain.PriceSource.PRODUCT),
                com.priceradar.pricing.domain.SnapshotStatus.REGULAR_PRICE
        );

        WalletEstimate estimate = new WalletEstimateService()
                .estimate(price, UserPricePreferences.defaults())
                .orElseThrow();

        assertThat(estimate.getAmount()).isEqualTo(RubleAmount.ofMinorUnits(114_900L));
        assertThat(estimate.getWalletDiscountPercent()).isEqualTo(3);
        assertThat(estimate.getSource()).isEqualTo(WalletPriceSource.ESTIMATED_BY_PERCENT);
    }

    @Test
    void displaysPricesInWholeRublesUsingMathematicalRounding() {
        assertThat(RublePriceFormatter.format(RubleAmount.ofMinorUnits(118_549L)))
                .isEqualTo("1 185 ₽");
        assertThat(RublePriceFormatter.format(RubleAmount.ofMinorUnits(118_550L)))
                .isEqualTo("1 186 ₽");
    }
}
