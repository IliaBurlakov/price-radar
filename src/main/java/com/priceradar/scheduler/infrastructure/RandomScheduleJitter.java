package com.priceradar.scheduler.infrastructure;

import com.priceradar.scheduler.application.ScheduleJitter;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomScheduleJitter implements ScheduleJitter {

    private final long maxJitterMillis;

    public RandomScheduleJitter(Duration maxJitter) {
        if (maxJitter == null || maxJitter.isNegative()) {
            throw new IllegalArgumentException("maxJitter must be non-negative");
        }
        this.maxJitterMillis = maxJitter.toMillis();
    }

    @Override
    public Duration next() {
        if (maxJitterMillis == 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(maxJitterMillis + 1));
    }
}
