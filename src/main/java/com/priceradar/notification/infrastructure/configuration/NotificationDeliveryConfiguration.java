package com.priceradar.notification.infrastructure.configuration;

import com.priceradar.notification.application.NotificationDeliveryService;
import com.priceradar.notification.application.NotificationDeliveryStore;
import com.priceradar.notification.application.NotificationMessageRenderer;
import com.priceradar.notification.infrastructure.NotificationDeliveryWorker;
import com.priceradar.pricing.application.WalletEstimateService;
import com.priceradar.telegram.application.TelegramGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(NotificationDeliveryProperties.class)
@ConditionalOnProperty(prefix = "priceradar.telegram", name = "enabled", havingValue = "true")
public class NotificationDeliveryConfiguration {

    @Bean
    public NotificationMessageRenderer notificationMessageRenderer() {
        return new NotificationMessageRenderer(new WalletEstimateService());
    }

    @Bean
    public NotificationDeliveryService notificationDeliveryService(
            NotificationDeliveryStore deliveryStore,
            NotificationMessageRenderer messageRenderer,
            TelegramGateway telegramGateway,
            Clock providerClock,
            NotificationDeliveryProperties properties
    ) {
        return new NotificationDeliveryService(
                deliveryStore,
                messageRenderer,
                telegramGateway,
                providerClock,
                properties.getBatchSize(),
                properties.getMaxAttempts(),
                properties.getClaimTimeout(),
                properties.getBaseBackoff(),
                properties.getMaxBackoff()
        );
    }

    @Bean
    public NotificationDeliveryWorker notificationDeliveryWorker(
            NotificationDeliveryService deliveryService
    ) {
        return new NotificationDeliveryWorker(deliveryService);
    }
}
