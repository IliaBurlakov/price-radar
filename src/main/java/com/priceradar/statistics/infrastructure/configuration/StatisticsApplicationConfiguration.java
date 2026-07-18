package com.priceradar.statistics.infrastructure.configuration;

import com.priceradar.statistics.application.PriceStatisticsStore;
import com.priceradar.statistics.application.SubscriptionStatisticsService;
import com.priceradar.tracking.application.SubscriptionStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StatisticsApplicationConfiguration {

    @Bean
    public SubscriptionStatisticsService subscriptionStatisticsService(
            SubscriptionStore subscriptionStore,
            PriceStatisticsStore priceStatisticsStore
    ) {
        return new SubscriptionStatisticsService(subscriptionStore, priceStatisticsStore);
    }
}
