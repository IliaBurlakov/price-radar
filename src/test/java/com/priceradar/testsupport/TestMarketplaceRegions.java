package com.priceradar.testsupport;

import com.priceradar.region.domain.MarketplaceRegion;
import com.priceradar.region.domain.MarketplaceRegionCode;

public final class TestMarketplaceRegions {

    private TestMarketplaceRegions() {
    }

    public static MarketplaceRegion moscow() {
        return new MarketplaceRegion(
                MarketplaceRegionCode.MOSCOW, "Москва", 1259570991L, 30, true, 1
        );
    }

    public static MarketplaceRegion irkutsk() {
        return new MarketplaceRegion(
                MarketplaceRegionCode.IRKUTSK, "Иркутск", -5827722L, 30, true, 6
        );
    }

    public static MarketplaceRegion bratsk() {
        return new MarketplaceRegion(
                MarketplaceRegionCode.BRATSK, "Братск", 123586041L, 30, true, 7
        );
    }
}
