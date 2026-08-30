package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.GeoTextNormalizer;

public final class GeoLocationLabelFormatter {

    public String format(GeoCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("candidate must not be null");
        StringBuilder label = new StringBuilder(candidate.getSettlementName());
        candidate.getRegionName()
                .filter(region -> !same(region, candidate.getSettlementName()))
                .ifPresent(region -> label.append(", ").append(region));
        candidate.getDistrictName()
                .filter(district -> !same(district, candidate.getSettlementName()))
                .filter(district -> candidate.getRegionName().map(region -> !same(district, region)).orElse(true))
                .ifPresent(district -> label.append(", ").append(district));
        return label.toString();
    }

    private boolean same(String first, String second) {
        return GeoTextNormalizer.normalize(first).equals(GeoTextNormalizer.normalize(second));
    }
}
