package com.priceradar.region.domain;

import java.time.Instant;

public final class WildberriesLocationContext {

    private final long destination;
    private final int spp;
    private final Instant resolvedAt;

    public WildberriesLocationContext(long destination, int spp, Instant resolvedAt) {
        if (destination == 0 || spp < 0 || resolvedAt == null) {
            throw new IllegalArgumentException("Wildberries location context is invalid");
        }
        this.destination = destination;
        this.spp = spp;
        this.resolvedAt = resolvedAt;
    }

    public long getDestination() { return destination; }
    public int getSpp() { return spp; }
    public Instant getResolvedAt() { return resolvedAt; }
}
