package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;
import com.priceradar.region.domain.ResolvedLocation;

import java.util.List;
import java.util.Optional;

public final class CitySelectionResult {

    public enum Status {
        SELECTED,
        CHANGED,
        UNCHANGED,
        OPTIONS,
        NOT_FOUND,
        GEOCODING_UNAVAILABLE,
        WILDBERRIES_UNAVAILABLE,
        ACTIVE_SUBSCRIPTIONS,
        INVALID_NUMBER,
        SESSION_EXPIRED,
        USER_NOT_FOUND
    }

    private final Status status;
    private final ResolvedLocation location;
    private final List<GeoCandidate> candidates;
    private final long activeSubscriptions;

    private CitySelectionResult(
            Status status,
            ResolvedLocation location,
            List<GeoCandidate> candidates,
            long activeSubscriptions
    ) {
        this.status = status;
        this.location = location;
        this.candidates = List.copyOf(candidates);
        this.activeSubscriptions = activeSubscriptions;
    }

    public static CitySelectionResult status(Status status) {
        return new CitySelectionResult(status, null, List.of(), 0);
    }

    public static CitySelectionResult selected(Status status, ResolvedLocation location) {
        return new CitySelectionResult(status, location, List.of(), 0);
    }

    public static CitySelectionResult options(List<GeoCandidate> candidates) {
        return new CitySelectionResult(Status.OPTIONS, null, candidates, 0);
    }

    public static CitySelectionResult blocked(long activeSubscriptions) {
        return new CitySelectionResult(Status.ACTIVE_SUBSCRIPTIONS, null, List.of(), activeSubscriptions);
    }

    public Status getStatus() { return status; }
    public Optional<ResolvedLocation> getLocation() { return Optional.ofNullable(location); }
    public List<GeoCandidate> getCandidates() { return candidates; }
    public long getActiveSubscriptions() { return activeSubscriptions; }
}
