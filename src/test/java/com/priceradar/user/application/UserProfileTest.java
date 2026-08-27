package com.priceradar.user.application;

import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileTest {

    @Test
    void unconfiguredProfileCannotSilentlyUseTechnicalMoscowContext() {
        UserProfile profile = new UserProfile(
                UUID.randomUUID(),
                7001L,
                7001L,
                null,
                UserPricePreferences.defaults()
        );

        assertThatThrownBy(profile::getPriceContext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before region selection");
    }
}
