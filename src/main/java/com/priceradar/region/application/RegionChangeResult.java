package com.priceradar.region.application;

import com.priceradar.region.domain.ResolvedLocation;

import java.util.Optional;

public final class RegionChangeResult {

    public enum Status {
        SELECTED,
        CHANGED,
        UNCHANGED,
        ACTIVE_SUBSCRIPTIONS,
        USER_NOT_FOUND
    }

    private final Status status;
    private final ResolvedLocation location;
    private final long activeSubscriptions;

    private RegionChangeResult(Status status, ResolvedLocation location, long activeSubscriptions) {
        this.status = status;
        this.location = location;
        this.activeSubscriptions = activeSubscriptions;
    }

    public static RegionChangeResult withLocation(Status status, ResolvedLocation location) {
        return new RegionChangeResult(status, location, 0);
    }

    public static RegionChangeResult blocked(ResolvedLocation location, long activeSubscriptions) {
        return new RegionChangeResult(Status.ACTIVE_SUBSCRIPTIONS, location, activeSubscriptions);
    }

    public static RegionChangeResult failed(Status status) {
        return new RegionChangeResult(status, null, 0);
    }

    public Status getStatus() { return status; }
    public Optional<ResolvedLocation> getLocation() { return Optional.ofNullable(location); }
    public long getActiveSubscriptions() { return activeSubscriptions; }
}
