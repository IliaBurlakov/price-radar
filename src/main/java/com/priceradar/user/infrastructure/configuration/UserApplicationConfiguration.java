package com.priceradar.user.infrastructure.configuration;

import com.priceradar.region.application.MarketplaceRegionStore;
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
            MarketplaceRegionStore regionStore,
            UserDefaultsProperties properties,
            Clock providerClock
    ) {
        UserPricePreferences defaultPricePreferences = new UserPricePreferences(
                properties.getWalletDiscountPercent()
        );
        return new UserProfileService(
                profileStore,
                regionStore,
                defaultPricePreferences,
                providerClock
        );
    }
}
