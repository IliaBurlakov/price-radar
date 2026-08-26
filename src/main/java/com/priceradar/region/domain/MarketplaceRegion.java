package com.priceradar.region.domain;

import com.priceradar.pricing.domain.PriceContext;

import java.util.Objects;

public final class MarketplaceRegion {

    private final MarketplaceRegionCode code;
    private final String displayName;
    private final long wbDestination;
    private final int wbSpp;
    private final boolean enabled;
    private final int sortOrder;

    public MarketplaceRegion(
            MarketplaceRegionCode code,
            String displayName,
            long wbDestination,
            int wbSpp,
            boolean enabled,
            int sortOrder
    ) {
        this.code = Objects.requireNonNull(code, "region code must not be null");
        if (displayName == null || displayName.isBlank() || wbDestination == 0
                || wbSpp < 0 || sortOrder <= 0) {
            throw new IllegalArgumentException("marketplace region fields are invalid");
        }
        this.displayName = displayName.trim();
        this.wbDestination = wbDestination;
        this.wbSpp = wbSpp;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }

    public MarketplaceRegionCode getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public long getWbDestination() { return wbDestination; }
    public int getWbSpp() { return wbSpp; }
    public boolean isEnabled() { return enabled; }
    public int getSortOrder() { return sortOrder; }

    public PriceContext toPriceContext() {
        return new PriceContext(displayName, wbDestination, wbSpp);
    }
}
