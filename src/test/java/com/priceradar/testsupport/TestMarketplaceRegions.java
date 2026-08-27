package com.priceradar.testsupport;

import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.GeoLocation;
import com.priceradar.region.domain.GeoLocationSource;
import com.priceradar.region.domain.ResolvedLocation;
import com.priceradar.region.domain.WildberriesLocationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TestMarketplaceRegions {

    private TestMarketplaceRegions() {
    }

    public static final UUID MOSCOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    public static final UUID IRKUTSK_ID = UUID.fromString("00000000-0000-0000-0000-000000000106");
    public static final UUID BRATSK_ID = UUID.fromString("00000000-0000-0000-0000-000000000107");

    public static ResolvedLocation moscow() {
        return location(MOSCOW_ID, "Москва", "Москва", 55.7558, 37.6173, 1259570991L);
    }

    public static ResolvedLocation irkutsk() {
        return location(IRKUTSK_ID, "Иркутск", "Иркутская область", 52.2864, 104.2807, -5827722L);
    }

    public static ResolvedLocation bratsk() {
        return location(BRATSK_ID, "Братск", "Иркутская область", 56.1514, 101.6342, 123586041L);
    }

    private static ResolvedLocation location(
            UUID id,
            String settlement,
            String region,
            double latitude,
            double longitude,
            long destination
    ) {
        GeoCandidate candidate = new GeoCandidate(
                settlement, region, null, "Россия",
                BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
                "legacy", id.toString(), "place", "city", 1.0, 16
        );
        return new ResolvedLocation(
                new GeoLocation(id, candidate, GeoLocationSource.LEGACY, Instant.EPOCH),
                new WildberriesLocationContext(destination, 30, Instant.EPOCH)
        );
    }
}
