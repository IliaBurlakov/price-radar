package com.priceradar.region.application;

import com.priceradar.region.domain.GeoCandidate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PendingCitySelection {

    private final UUID id;
    private final UUID userId;
    private final List<GeoCandidate> candidates;
    private final Instant createdAt;
    private final Instant expiresAt;

    public PendingCitySelection(
            UUID id,
            UUID userId,
            List<GeoCandidate> candidates,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (id == null || userId == null || candidates == null || createdAt == null || expiresAt == null
                || candidates.stream().anyMatch(candidate -> candidate == null)
                || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("pending city selection fields are invalid");
        }
        this.id = id;
        this.userId = userId;
        this.candidates = List.copyOf(candidates);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public List<GeoCandidate> getCandidates() { return candidates; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
}
