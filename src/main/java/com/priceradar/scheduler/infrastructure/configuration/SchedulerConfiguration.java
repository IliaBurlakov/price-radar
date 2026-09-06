package com.priceradar.scheduler.infrastructure.configuration;

import com.priceradar.marketplace.application.MarketplaceProvider;
import com.priceradar.notification.application.NotificationDecisionService;
import com.priceradar.notification.application.NotificationOutboxWriter;
import com.priceradar.pricing.application.PriceSemanticsService;
import com.priceradar.scheduler.application.DueWatchTargetReader;
import com.priceradar.scheduler.application.NotificationFanOutService;
import com.priceradar.scheduler.application.NotificationFanOutJobService;
import com.priceradar.scheduler.application.NotificationFanOutJobStore;
import com.priceradar.scheduler.application.ScheduleJitter;
import com.priceradar.scheduler.application.ScheduledObservationStore;
import com.priceradar.scheduler.application.WatchTargetCheckService;
import com.priceradar.scheduler.application.WatchTargetCheckTransaction;
import com.priceradar.scheduler.application.SubscriptionNotificationProcessor;
import com.priceradar.scheduler.infrastructure.RandomScheduleJitter;
import com.priceradar.scheduler.infrastructure.NotificationFanOutWorker;
import com.priceradar.scheduler.infrastructure.WatchTargetSchedulerWorker;
import com.priceradar.tracking.application.SubscriptionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({SchedulerProperties.class, NotificationFanOutProperties.class})
public class SchedulerConfiguration {

    @Bean
    public ScheduleJitter scheduleJitter(SchedulerProperties properties) {
        return new RandomScheduleJitter(properties.getMaxJitter());
    }

    @Bean
    public WatchTargetCheckTransaction watchTargetCheckTransaction(
            ScheduledObservationStore observationStore,
            NotificationFanOutJobStore fanOutJobStore
    ) {
        return new WatchTargetCheckTransaction(observationStore, fanOutJobStore);
    }

    @Bean
    public SubscriptionNotificationProcessor subscriptionNotificationProcessor(
            SubscriptionStore subscriptionStore,
            NotificationDecisionService decisionService,
            NotificationOutboxWriter outboxWriter
    ) {
        return new SubscriptionNotificationProcessor(
                subscriptionStore,
                decisionService,
                outboxWriter
        );
    }

    @Bean
    public NotificationFanOutService notificationFanOutService(
            SubscriptionStore subscriptionStore,
            SubscriptionNotificationProcessor notificationProcessor
    ) {
        return new NotificationFanOutService(subscriptionStore, notificationProcessor);
    }

    @Bean
    public WatchTargetCheckService watchTargetCheckService(
            List<MarketplaceProvider> marketplaceProviders,
            PriceSemanticsService priceSemanticsService,
            WatchTargetCheckTransaction checkTransaction,
            ScheduleJitter scheduleJitter,
            Clock providerClock,
            SchedulerProperties properties
    ) {
        return new WatchTargetCheckService(
                marketplaceProviders,
                priceSemanticsService,
                checkTransaction,
                scheduleJitter,
                providerClock,
                properties.getRefreshInterval(),
                properties.getFailureRetryDelay()
        );
    }

    @Bean
    public NotificationFanOutJobService notificationFanOutJobService(
            NotificationFanOutJobStore jobStore,
            NotificationFanOutService fanOutService,
            Clock providerClock,
            NotificationFanOutProperties properties
    ) {
        return new NotificationFanOutJobService(
                jobStore,
                fanOutService,
                providerClock,
                properties.getBatchSize(),
                properties.getClaimTimeout(),
                properties.getBaseBackoff(),
                properties.getMaxBackoff()
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "priceradar.notification.fan-out",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public NotificationFanOutWorker notificationFanOutWorker(
            NotificationFanOutJobService jobService
    ) {
        return new NotificationFanOutWorker(jobService);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "priceradar.scheduler",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public WatchTargetSchedulerWorker watchTargetSchedulerWorker(
            DueWatchTargetReader dueTargetReader,
            WatchTargetCheckService checkService,
            Clock providerClock,
            SchedulerProperties properties
    ) {
        return new WatchTargetSchedulerWorker(
                dueTargetReader,
                checkService,
                providerClock,
                properties.getBatchSize()
        );
    }
}
