package com.priceradar.marketplace.application;

import com.priceradar.tracking.domain.WatchKey;

public final class MarketplaceCurrentProductRequest {

    private final WatchKey watchKey;

    public MarketplaceCurrentProductRequest(WatchKey watchKey) {
        if (watchKey == null)
            throw new IllegalArgumentException("watchKey must not be null!");
        this.watchKey = watchKey;
    }

    public WatchKey getWatchKey() {
        return watchKey;
    }
}
