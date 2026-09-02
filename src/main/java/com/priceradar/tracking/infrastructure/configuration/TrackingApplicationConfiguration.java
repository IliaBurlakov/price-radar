package com.priceradar.tracking.infrastructure.configuration;

import com.priceradar.configuration.PriceRadarPolicyProperties;
import com.priceradar.tracking.application.LatestSnapshotQueryService;
import com.priceradar.tracking.application.InitialThresholdNotificationEnqueuer;
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
            SubscriptionStore subscriptionStore,
            InitialThresholdNotificationEnqueuer thresholdNotificationEnqueuer,
            PriceRadarPolicyProperties policy
    ) {
        return new SubscriptionService(
                userProfileStore,
                subscriptionStore,
                thresholdNotificationEnqueuer,
                policy.getQuoteTtl()
        );
    }

    @Bean
    public LatestSnapshotQueryService latestSnapshotQueryService(
            SubscriptionStore subscriptionStore
    ) {
        return new LatestSnapshotQueryService(subscriptionStore);
    }
}
