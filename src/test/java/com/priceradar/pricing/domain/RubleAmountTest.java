package com.priceradar.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class RubleAmountTest {

    @Test
    void createsNonNegativeAmountInMinorUnits() {
        RubleAmount amount = RubleAmount.ofMinorUnits(12_345);

        assertThat(amount.getMinorUnits()).isEqualTo(12_345);
    }

    @Test
    void permitsZeroAmount() {
        assertThat(RubleAmount.ofMinorUnits(0).getMinorUnits()).isZero();
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatIllegalArgumentException().isThrownBy(() -> RubleAmount.ofMinorUnits(-1));
    }
}
