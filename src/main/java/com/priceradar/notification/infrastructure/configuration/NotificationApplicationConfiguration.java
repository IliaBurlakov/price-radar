package com.priceradar.notification.infrastructure.configuration;

import com.priceradar.notification.application.NotificationDecisionService;
import com.priceradar.notification.application.NotificationOutboxStore;
import com.priceradar.notification.application.NotificationOutboxWriter;
import com.priceradar.tracking.application.SubscriptionStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NotificationApplicationConfiguration {

    @Bean
    public NotificationDecisionService notificationDecisionService() {
        return new NotificationDecisionService();
    }

    @Bean
    public NotificationOutboxWriter notificationOutboxWriter(
            SubscriptionStore subscriptionStore,
            NotificationOutboxStore outboxStore
    ) {
        return new NotificationOutboxWriter(subscriptionStore, outboxStore);
    }
}
