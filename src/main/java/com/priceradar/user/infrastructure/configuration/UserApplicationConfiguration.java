package com.priceradar.user.infrastructure.configuration;

import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.user.application.UserProfileService;
import com.priceradar.user.application.UserProfileStore;
import com.priceradar.user.domain.UserPricePreferences;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserDefaultsProperties.class)
public class UserApplicationConfiguration {

    @Bean
    public UserProfileService userProfileService(
            UserProfileStore profileStore,
            UserDefaultsProperties properties,
            Clock providerClock
    ) {
        PriceContext defaultPriceContext = new PriceContext(
                properties.getCityName(),
                properties.getDest(),
                properties.getSpp()
        );
        UserPricePreferences defaultPricePreferences = new UserPricePreferences(
                properties.getWalletDiscountPercent()
        );
        return new UserProfileService(
                profileStore,
                defaultPriceContext,
                defaultPricePreferences,
                providerClock
        );
    }
}
