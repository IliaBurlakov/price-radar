package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;

import java.util.List;

public final class GeocodingResult {

    public enum Status { SUCCESS, TEMPORARILY_UNAVAILABLE }

    private final Status status;
    private final List<GeoCandidate> candidates;

    private GeocodingResult(Status status, List<GeoCandidate> candidates) {
        this.status = status;
        this.candidates = List.copyOf(candidates);
    }

    public static GeocodingResult success(List<GeoCandidate> candidates) {
        if (candidates == null || candidates.stream().anyMatch(candidate -> candidate == null)) {
            throw new IllegalArgumentException("geocoding candidates must not be null");
        }
        return new GeocodingResult(Status.SUCCESS, candidates);
    }

    public static GeocodingResult temporarilyUnavailable() {
        return new GeocodingResult(Status.TEMPORARILY_UNAVAILABLE, List.of());
    }

    public Status getStatus() { return status; }
    public List<GeoCandidate> getCandidates() { return candidates; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
}
