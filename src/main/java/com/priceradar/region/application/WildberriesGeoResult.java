package com.priceradar.region.application;

import com.priceradar.region.domain.WildberriesLocationContext;

import java.util.Optional;

public final class WildberriesGeoResult {

    private final WildberriesLocationContext context;

    private WildberriesGeoResult(WildberriesLocationContext context) {
        this.context = context;
    }

    public static WildberriesGeoResult success(WildberriesLocationContext context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        return new WildberriesGeoResult(context);
    }

    public static WildberriesGeoResult temporarilyUnavailable() {
        return new WildberriesGeoResult(null);
    }

    public boolean isSuccess() { return context != null; }
    public Optional<WildberriesLocationContext> getContext() { return Optional.ofNullable(context); }
}
