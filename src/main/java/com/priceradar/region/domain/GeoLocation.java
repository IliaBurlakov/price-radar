package com.priceradar.region.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class GeoLocation {

    private final UUID id;
    private final GeoCandidate candidate;
    private final GeoLocationSource source;
    private final Instant createdAt;

    public GeoLocation(UUID id, GeoCandidate candidate, GeoLocationSource source, Instant createdAt) {
        if (id == null || candidate == null || source == null || createdAt == null) {
            throw new IllegalArgumentException("geo location fields must not be null");
        }
        this.id = id;
        this.candidate = candidate;
        this.source = source;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getSettlementName() { return candidate.getSettlementName(); }
    public Optional<String> getRegionName() { return candidate.getRegionName(); }
    public Optional<String> getDistrictName() { return candidate.getDistrictName(); }
    public String getCountryName() { return candidate.getCountryName(); }
    public BigDecimal getLatitude() { return candidate.getLatitude(); }
    public BigDecimal getLongitude() { return candidate.getLongitude(); }
    public String getNormalizedName() { return candidate.getNormalizedName(); }
    public String getNormalizedRegion() { return candidate.getNormalizedRegion(); }
    public String getNormalizedDistrict() { return candidate.getNormalizedDistrict(); }
    public String getIdentityKey() { return candidate.identityKey(); }
    public Optional<String> getProviderObjectType() { return candidate.getProviderObjectType(); }
    public Optional<String> getProviderObjectId() { return candidate.getProviderObjectId(); }
    public GeoLocationSource getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }

    public GeoCandidate toCandidate() { return candidate; }
}
