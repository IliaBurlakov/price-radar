package com.priceradar.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceContextTest {

    @Test
    void createsConstitutionMoscowDefaults() {
        PriceContext context = PriceContext.moscow();

        assertThat(context.cityName()).isEqualTo("Moscow");
        assertThat(context.dest()).isEqualTo(1_259_570_991L);
        assertThat(context.spp()).isEqualTo(30);
        assertThat(context.walletDiscountPercent()).isEqualByComparingTo("3");
    }

    @Test
    void createsTestedNovosibirskProfileWithUserDiscount() {
        PriceContext context = PriceContext.novosibirsk(new BigDecimal("4.5"));

        assertThat(context.cityName()).isEqualTo("Novosibirsk");
        assertThat(context.dest()).isEqualTo(-366_519L);
        assertThat(context.spp()).isEqualTo(30);
        assertThat(context.walletDiscountPercent()).isEqualByComparingTo("4.5");
    }

    @Test
    void rejectsInvalidContextValues() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PriceContext(" ", 1, 30, BigDecimal.ZERO)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PriceContext("Moscow", 0, 30, BigDecimal.ZERO)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PriceContext("Moscow", 1, 101, BigDecimal.ZERO)
        );
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PriceContext("Moscow", 1, 30, new BigDecimal("100.01"))
        );
    }
}
