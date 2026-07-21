package com.priceradar.tracking.infrastructure.configuration;

import com.priceradar.tracking.application.LatestSnapshotQueryService;
import com.priceradar.tracking.application.SubscriptionService;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.user.application.UserProfileStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TrackingApplicationConfiguration {

    @Bean
    public SubscriptionService subscriptionService(
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore
    ) {
        return new SubscriptionService(userProfileStore, subscriptionStore);
    }

    @Bean
    public LatestSnapshotQueryService latestSnapshotQueryService(
            SubscriptionStore subscriptionStore
    ) {
        return new LatestSnapshotQueryService(subscriptionStore);
    }
}
