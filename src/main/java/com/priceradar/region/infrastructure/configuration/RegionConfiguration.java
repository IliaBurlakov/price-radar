package com.priceradar.region.infrastructure.configuration;

import com.priceradar.region.application.MarketplaceRegionStore;
import com.priceradar.region.application.UserRegionService;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfileStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RegionConfiguration {

    @Bean
    public UserRegionService userRegionService(
            MarketplaceRegionStore regionStore,
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore
    ) {
        return new UserRegionService(regionStore, userProfileStore, subscriptionStore);
    }
}
