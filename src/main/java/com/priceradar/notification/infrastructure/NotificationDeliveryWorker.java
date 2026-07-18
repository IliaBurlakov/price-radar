package com.priceradar.notification.infrastructure;

import com.priceradar.notification.application.NotificationDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class NotificationDeliveryWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryWorker.class);

    private final NotificationDeliveryService deliveryService;

    public NotificationDeliveryWorker(NotificationDeliveryService deliveryService) {
        if (deliveryService == null) {
            throw new IllegalArgumentException("deliveryService must not be null");
        }
        this.deliveryService = deliveryService;
    }

    @Scheduled(
            initialDelayString = "${priceradar.notification.delivery.initial-delay:PT5S}",
            fixedDelayString = "${priceradar.notification.delivery.poll-delay:PT5S}"
    )
    public void deliver() {
        try {
            deliveryService.deliverDue();
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Notification delivery batch failed, errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
