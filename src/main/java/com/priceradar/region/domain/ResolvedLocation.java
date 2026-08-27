package com.priceradar.region.domain;

import com.priceradar.pricing.domain.PriceContext;

public final class ResolvedLocation {

    private final GeoLocation location;
    private final WildberriesLocationContext wildberriesContext;

    public ResolvedLocation(GeoLocation location, WildberriesLocationContext wildberriesContext) {
        if (location == null || wildberriesContext == null) {
            throw new IllegalArgumentException("resolved location fields must not be null");
        }
        this.location = location;
        this.wildberriesContext = wildberriesContext;
    }

    public GeoLocation getLocation() { return location; }
    public WildberriesLocationContext getWildberriesContext() { return wildberriesContext; }

    public PriceContext toPriceContext() {
        return new PriceContext(
                location.getSettlementName(),
                wildberriesContext.getDestination(),
                wildberriesContext.getSpp()
        );
    }
}
