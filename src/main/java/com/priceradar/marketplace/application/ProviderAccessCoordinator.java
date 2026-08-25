package com.priceradar.marketplace.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import com.priceradar.marketplace.domain.Marketplace;

public final class ProviderAccessCoordinator {
    private static final Duration STATE_FAILURE_DELAY = Duration.ofMinutes(1);

    private final Semaphore singleRequest;
    private final Duration minDelay;
    private final Clock clock;
    private final Marketplace marketplace;
    private final ProviderCooldownStore cooldownStore;
    private final Object timingLock;
    private Instant nextAllowedAt;

    public ProviderAccessCoordinator(
            Duration minDelay,
            Clock clock,
            Marketplace marketplace,
            ProviderCooldownStore cooldownStore
    ) {
        if (minDelay == null) {
            throw new IllegalArgumentException("minDelay must not be null!");
        }
        if (minDelay.isNegative()) {
            throw new IllegalArgumentException("minDelay must not be negative!");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null!");
        }
        if (marketplace == null) {
            throw new IllegalArgumentException("marketplace must not be null!");
        }
        if (cooldownStore == null) {
            throw new IllegalArgumentException("cooldownStore must not be null!");
        }

        this.minDelay = minDelay;
        this.clock = clock;
        this.marketplace = marketplace;
        this.cooldownStore = cooldownStore;
        this.singleRequest = new Semaphore(1, true);
        this.timingLock = new Object();
        this.nextAllowedAt = clock.instant();
    }

    public void awaitTurn() throws InterruptedException, ProviderCooldownActiveException {
        singleRequest.acquire();

        try {
            while (true) {
                Duration remainingDelay;

                synchronized (timingLock) {
                    Instant now = clock.instant();
                    Optional<Instant> persistedCooldown;

                    try {
                        persistedCooldown = cooldownStore.findCooldownUntil(marketplace);
                    } catch (RuntimeException exception) {
                        Instant safeRetryAt = now.plus(STATE_FAILURE_DELAY);
                        if (nextAllowedAt.isAfter(safeRetryAt))
                            safeRetryAt = nextAllowedAt;
                        nextAllowedAt = safeRetryAt;
                        throw new ProviderCooldownActiveException(safeRetryAt, exception);
                    }

                    if (persistedCooldown.isPresent() && now.isBefore(persistedCooldown.get())) {
                        throw new ProviderCooldownActiveException(persistedCooldown.get());
                    }

                    if (!now.isBefore(nextAllowedAt)) {
                        return;
                    }

                    remainingDelay = Duration.between(now, nextAllowedAt);
                }
                Thread.sleep(remainingDelay);
            }
        } catch (InterruptedException exception) {
            singleRequest.release();
            throw exception;
        } catch (ProviderCooldownActiveException exception) {
            singleRequest.release();
            throw exception;
        } catch (RuntimeException exception) {
            singleRequest.release();
            throw exception;
        }
    }

    /**
     * Must be called exactly once after every successful awaitTurn().
     */
    public void completeAfter(Duration delay) {
        try {
            if (delay == null)
                throw new IllegalArgumentException("delay must not be null!");
            if (delay.isNegative())
                throw new IllegalArgumentException("delay must not be negative!");

            Instant now = clock.instant();
            Duration effectiveDelay = delay.compareTo(minDelay) > 0 ? delay : minDelay;

            synchronized (timingLock) {
                nextAllowedAt = now.plus(effectiveDelay);
            }
        } finally {
            singleRequest.release();
        }
    }

    public boolean completeWithCooldown(Instant cooldownUntil) {
        try {
            return storeCooldown(cooldownUntil);
        } finally {
            singleRequest.release();
        }
    }

    /**
     * Activates a cooldown after response processing has already released the request permit.
     */
    public boolean activateCooldown(Instant cooldownUntil) throws InterruptedException {
        singleRequest.acquire();
        try {
            return storeCooldown(cooldownUntil);
        } finally {
            singleRequest.release();
        }
    }

    private boolean storeCooldown(Instant cooldownUntil) {
        if (cooldownUntil == null)
            throw new IllegalArgumentException("cooldownUntil must not be null!");

        Instant now = clock.instant();
        Instant minimumNextRequestAt = now.plus(minDelay);
        Instant nextRequestAt = cooldownUntil.isAfter(minimumNextRequestAt)
                ? cooldownUntil
                : minimumNextRequestAt;

        synchronized (timingLock) {
            nextAllowedAt = nextRequestAt;
        }

        if (cooldownUntil.isAfter(now)) {
            try {
                cooldownStore.saveCooldownUntil(marketplace, cooldownUntil, now);
            } catch (RuntimeException exception) {
                return false;
            }
        }
        return true;
    }

    public Duration getMinDelay() {
        return minDelay;
    }
}
