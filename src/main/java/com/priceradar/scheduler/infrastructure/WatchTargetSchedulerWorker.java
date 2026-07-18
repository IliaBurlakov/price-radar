package com.priceradar.scheduler.infrastructure;

import com.priceradar.scheduler.application.DueWatchTarget;
import com.priceradar.scheduler.application.DueWatchTargetReader;
import com.priceradar.scheduler.application.WatchTargetCheckOutcome;
import com.priceradar.scheduler.application.WatchTargetCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class WatchTargetSchedulerWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchTargetSchedulerWorker.class);

    private final DueWatchTargetReader dueTargetReader;
    private final WatchTargetCheckService checkService;
    private final Clock clock;
    private final int batchSize;

    public WatchTargetSchedulerWorker(
            DueWatchTargetReader dueTargetReader,
            WatchTargetCheckService checkService,
            Clock clock,
            int batchSize
    ) {
        if (dueTargetReader == null || checkService == null || clock == null) {
            throw new IllegalArgumentException("scheduler worker dependencies must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.dueTargetReader = dueTargetReader;
        this.checkService = checkService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${priceradar.scheduler.initial-delay:PT10S}",
            fixedDelayString = "${priceradar.scheduler.poll-delay:PT60S}"
    )
    public void runDueChecks() {
        List<DueWatchTarget> targets = dueTargetReader.findDue(clock.instant(), batchSize);
        for (DueWatchTarget target : targets) {
            try {
                WatchTargetCheckOutcome outcome = checkService.check(
                        target,
                        UUID.randomUUID()
                );
                if (outcome == WatchTargetCheckOutcome.PROVIDER_COOLDOWN) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Scheduled watch target check failed unexpectedly, watchTargetId={}, errorType={}",
                        target.getWatchTargetId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
    }
}
