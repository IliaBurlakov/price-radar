package com.priceradar.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class UserPricePreferencesTest {

    @Test
    void providesDefaultWalletDiscount() {
        UserPricePreferences preferences = UserPricePreferences.defaults();

        assertThat(preferences.getWalletDiscountPercent()).isEqualTo(3);
    }

    @Test
    void acceptsBoundaryValues() {
        assertThat(new UserPricePreferences(0).getWalletDiscountPercent()).isZero();
        assertThat(new UserPricePreferences(100).getWalletDiscountPercent()).isEqualTo(100);
    }

    @Test
    void rejectsValuesOutsideSupportedRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserPricePreferences(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> new UserPricePreferences(101));
    }
}
