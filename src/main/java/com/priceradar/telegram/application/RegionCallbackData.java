package com.priceradar.telegram.application;

import com.priceradar.region.domain.MarketplaceRegionCode;

import java.util.Optional;

public final class RegionCallbackData {

    public static final String OPEN = "REGION:OPEN";
    private static final String SELECT_PREFIX = "REGION:SELECT:";

    private RegionCallbackData() {
    }

    public static String select(MarketplaceRegionCode code) {
        if (code == null) {
            throw new IllegalArgumentException("region code must not be null");
        }
        return SELECT_PREFIX + code.name();
    }

    public static Optional<MarketplaceRegionCode> parseSelection(String data) {
        if (data == null || !data.startsWith(SELECT_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MarketplaceRegionCode.valueOf(data.substring(SELECT_PREFIX.length())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
