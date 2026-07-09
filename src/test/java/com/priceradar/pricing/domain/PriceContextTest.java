package com.priceradar.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.priceradar.testsupport.TestPriceContexts;
import org.junit.jupiter.api.Test;

class PriceContextTest {

    @Test
    void containsOnlyProviderPriceContext() {
        PriceContext context = TestPriceContexts.moscow();

        assertThat(context.getCityName()).isEqualTo("Moscow");
        assertThat(context.getDest()).isEqualTo(1_259_570_991L);
        assertThat(context.getSpp()).isEqualTo(30);
    }

    @Test
    void acceptsNonNegativeSppWithoutUnconfirmedUpperLimit() {
        PriceContext context = new PriceContext("Moscow", 1_259_570_991L, 150);

        assertThat(context.getSpp()).isEqualTo(150);
    }

    @Test
    void rejectsInvalidProviderContext() {
        assertThatNullPointerException().isThrownBy(() -> new PriceContext(null, 1, 30));
        assertThatIllegalArgumentException().isThrownBy(() -> new PriceContext(" ", 1, 30));
        assertThatIllegalArgumentException().isThrownBy(() -> new PriceContext("Moscow", 0, 30));
        assertThatIllegalArgumentException().isThrownBy(() -> new PriceContext("Moscow", 1, -1));
    }
}
