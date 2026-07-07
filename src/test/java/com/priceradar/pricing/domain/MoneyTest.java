package com.priceradar.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void createsNonNegativeRubleAmount() {
        Money money = Money.ofRubMinor(12_345);

        assertThat(money.minorUnits()).isEqualTo(12_345);
        assertThat(money.currencyCode()).isEqualTo("RUB");
    }

    @Test
    void permitsZeroForFullyDiscountedEstimate() {
        assertThat(Money.ofRubMinor(0).minorUnits()).isZero();
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.ofRubMinor(-1));
    }
}
