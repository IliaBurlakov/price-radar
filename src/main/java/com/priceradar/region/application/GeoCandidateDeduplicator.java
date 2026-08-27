package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GeoCandidateDeduplicator {

    private static final double SAME_PLACE_MAX_DISTANCE_KM = 10.0;

    public List<GeoCandidate> deduplicate(List<GeoCandidate> candidates) {
        if (candidates == null) throw new IllegalArgumentException("candidates must not be null");
        List<GeoCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(this::quality).reversed())
                .toList();
        List<GeoCandidate> result = new ArrayList<>();
        for (GeoCandidate candidate : sorted) {
            if (result.stream().noneMatch(existing -> sameLogicalPlace(existing, candidate))) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    private boolean sameLogicalPlace(GeoCandidate first, GeoCandidate second) {
        if (!first.getNormalizedName().equals(second.getNormalizedName())
                || !first.getNormalizedRegion().equals(second.getNormalizedRegion())) {
            return false;
        }
        if (!first.getNormalizedDistrict().isBlank()
                && !second.getNormalizedDistrict().isBlank()
                && !first.getNormalizedDistrict().equals(second.getNormalizedDistrict())) {
            return false;
        }
        return distanceKm(first, second) <= SAME_PLACE_MAX_DISTANCE_KM;
    }

    private double quality(GeoCandidate candidate) {
        double place = candidate.getCategory().filter("place"::equals).isPresent() ? 10 : 0;
        double preferredType = candidate.getPlaceType()
                .filter(type -> List.of("city", "town", "village", "settlement", "hamlet").contains(type))
                .isPresent() ? 5 : 0;
        return place + preferredType + candidate.getImportance() - candidate.getPlaceRank() / 1000.0;
    }

    private double distanceKm(GeoCandidate first, GeoCandidate second) {
        double firstLat = Math.toRadians(first.getLatitude().doubleValue());
        double secondLat = Math.toRadians(second.getLatitude().doubleValue());
        double deltaLat = secondLat - firstLat;
        double deltaLon = Math.toRadians(
                second.getLongitude().doubleValue() - first.getLongitude().doubleValue()
        );
        double haversine = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(firstLat) * Math.cos(secondLat)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }
}
