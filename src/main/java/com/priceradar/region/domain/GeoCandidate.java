package com.priceradar.region.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public final class GeoCandidate {

    private final String settlementName;
    private final String regionName;
    private final String districtName;
    private final String countryName;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final String providerObjectType;
    private final String providerObjectId;
    private final String category;
    private final String placeType;
    private final double importance;
    private final int placeRank;

    public GeoCandidate(
            String settlementName,
            String regionName,
            String districtName,
            String countryName,
            BigDecimal latitude,
            BigDecimal longitude,
            String providerObjectType,
            String providerObjectId,
            String category,
            String placeType,
            double importance,
            int placeRank
    ) {
        this.settlementName = required(settlementName, "settlement name", 100);
        this.regionName = optional(regionName, 160);
        this.districtName = optional(districtName, 200);
        this.countryName = required(countryName, "country name", 100);
        this.latitude = coordinate(latitude, -90, 90, "latitude");
        this.longitude = coordinate(longitude, -180, 180, "longitude");
        this.providerObjectType = optional(providerObjectType, 32);
        this.providerObjectId = optional(providerObjectId, 80);
        this.category = optional(category, 40);
        this.placeType = optional(placeType, 40);
        if (!Double.isFinite(importance) || importance < 0 || placeRank < 0) {
            throw new IllegalArgumentException("candidate quality fields are invalid");
        }
        this.importance = importance;
        this.placeRank = placeRank;
    }

    public String getSettlementName() { return settlementName; }
    public Optional<String> getRegionName() { return optionalValue(regionName); }
    public Optional<String> getDistrictName() { return optionalValue(districtName); }
    public String getCountryName() { return countryName; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public Optional<String> getProviderObjectType() { return optionalValue(providerObjectType); }
    public Optional<String> getProviderObjectId() { return optionalValue(providerObjectId); }
    public Optional<String> getCategory() { return optionalValue(category); }
    public Optional<String> getPlaceType() { return optionalValue(placeType); }
    public double getImportance() { return importance; }
    public int getPlaceRank() { return placeRank; }

    public String getNormalizedName() { return GeoTextNormalizer.normalize(settlementName); }
    public String getNormalizedRegion() { return GeoTextNormalizer.normalize(regionName); }
    public String getNormalizedDistrict() { return GeoTextNormalizer.normalize(districtName); }

    public String identityKey() {
        return getNormalizedName() + '|' + getNormalizedRegion() + '|' + getNormalizedDistrict()
                + '|' + latitude.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString()
                + '|' + longitude.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private Optional<String> optionalValue(String value) {
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private String required(String value, String name, int maxLength) {
        String normalized = optional(value, maxLength);
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    private String optional(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new IllegalArgumentException("location text is too long");
        }
        return normalized;
    }

    private BigDecimal coordinate(BigDecimal value, int minimum, int maximum, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.compareTo(BigDecimal.valueOf(minimum)) < 0
                || value.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw new IllegalArgumentException(name + " is out of range");
        }
        return value;
    }
}
