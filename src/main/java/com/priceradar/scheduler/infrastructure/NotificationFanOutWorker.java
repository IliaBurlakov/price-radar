package com.priceradar.scheduler.infrastructure;

import com.priceradar.scheduler.application.NotificationFanOutJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class NotificationFanOutWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationFanOutWorker.class);

    private final NotificationFanOutJobService jobService;

    public NotificationFanOutWorker(NotificationFanOutJobService jobService) {
        if (jobService == null) {
            throw new IllegalArgumentException("fan-out jobService must not be null");
        }
        this.jobService = jobService;
    }

    @Scheduled(
            initialDelayString = "${priceradar.notification.fan-out.initial-delay:PT15S}",
            fixedDelayString = "${priceradar.notification.fan-out.poll-delay:PT30S}"
    )
    public void processDueJobs() {
        try {
            jobService.processDue();
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Notification fan-out batch failed, errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
