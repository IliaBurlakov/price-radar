package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;

public interface WildberriesGeoProvider {

    WildberriesGeoResult resolve(GeoCandidate candidate);
}
