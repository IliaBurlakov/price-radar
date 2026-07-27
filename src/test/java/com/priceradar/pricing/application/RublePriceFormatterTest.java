package com.priceradar.pricing.application;

import com.priceradar.pricing.domain.RubleAmount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RublePriceFormatterTest {

    @Test
    void roundsToWholeRublesAndOmitsKopecks() {
        assertThat(RublePriceFormatter.format(RubleAmount.ofMinorUnits(118_534)))
                .isEqualTo("1 185 ₽");
        assertThat(RublePriceFormatter.format(RubleAmount.ofMinorUnits(118_549)))
                .isEqualTo("1 185 ₽");
        assertThat(RublePriceFormatter.format(RubleAmount.ofMinorUnits(118_550)))
                .isEqualTo("1 186 ₽");
    }
}
