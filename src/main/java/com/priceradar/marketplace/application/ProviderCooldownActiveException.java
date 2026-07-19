package com.priceradar.marketplace.application;

import java.time.Instant;

public final class ProviderCooldownActiveException extends Exception {

    private final Instant cooldownUntil;
    private final boolean stateUnavailable;

    public ProviderCooldownActiveException(Instant cooldownUntil) {
        super("Provider cooldown is active until " + cooldownUntil);
        if (cooldownUntil == null)
            throw new IllegalArgumentException("cooldownUntil must not be null!");
        this.cooldownUntil = cooldownUntil;
        this.stateUnavailable = false;
    }

    public ProviderCooldownActiveException(Instant cooldownUntil, RuntimeException cause) {
        super("Provider safety state is temporarily unavailable", cause);
        if (cooldownUntil == null)
            throw new IllegalArgumentException("cooldownUntil must not be null!");
        if (cause == null)
            throw new IllegalArgumentException("cause must not be null!");
        this.cooldownUntil = cooldownUntil;
        this.stateUnavailable = true;
    }

    public Instant getCooldownUntil() {
        return cooldownUntil;
    }

    public boolean isStateUnavailable() {
        return stateUnavailable;
    }
}
