package com.priceradar.region.application;

import com.priceradar.region.domain.MarketplaceRegion;

import java.util.Optional;

public final class RegionChangeResult {

    public enum Status { CHANGED, UNCHANGED, ACTIVE_SUBSCRIPTIONS, REGION_NOT_FOUND, USER_NOT_FOUND }

    private final Status status;
    private final MarketplaceRegion region;
    private final long activeSubscriptions;

    private RegionChangeResult(Status status, MarketplaceRegion region, long activeSubscriptions) {
        this.status = status;
        this.region = region;
        this.activeSubscriptions = activeSubscriptions;
    }

    public static RegionChangeResult withRegion(Status status, MarketplaceRegion region) {
        return new RegionChangeResult(status, region, 0);
    }

    public static RegionChangeResult blocked(MarketplaceRegion region, long activeSubscriptions) {
        return new RegionChangeResult(Status.ACTIVE_SUBSCRIPTIONS, region, activeSubscriptions);
    }

    public static RegionChangeResult failed(Status status) {
        return new RegionChangeResult(status, null, 0);
    }

    public Status getStatus() { return status; }
    public Optional<MarketplaceRegion> getRegion() { return Optional.ofNullable(region); }
    public long getActiveSubscriptions() { return activeSubscriptions; }
}
