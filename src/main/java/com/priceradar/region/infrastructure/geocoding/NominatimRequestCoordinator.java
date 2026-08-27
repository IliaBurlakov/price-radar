package com.priceradar.region.infrastructure.geocoding;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;

public final class NominatimRequestCoordinator {

    private final Semaphore singleRequest = new Semaphore(1, true);
    private final Duration minRequestInterval;
    private final Clock clock;
    private Instant nextAllowedAt;

    public NominatimRequestCoordinator(Duration minRequestInterval, Clock clock) {
        if (minRequestInterval == null || minRequestInterval.isNegative() || clock == null) {
            throw new IllegalArgumentException("Nominatim request coordinator settings are invalid");
        }
        this.minRequestInterval = minRequestInterval;
        this.clock = clock;
        this.nextAllowedAt = clock.instant();
    }

    public void awaitTurn() throws InterruptedException {
        singleRequest.acquire();
        try {
            Instant now = clock.instant();
            if (now.isBefore(nextAllowedAt)) {
                Thread.sleep(Duration.between(now, nextAllowedAt));
            }
        } catch (InterruptedException | RuntimeException exception) {
            singleRequest.release();
            throw exception;
        }
    }

    public void complete() {
        try {
            nextAllowedAt = clock.instant().plus(minRequestInterval);
        } finally {
            singleRequest.release();
        }
    }
}
