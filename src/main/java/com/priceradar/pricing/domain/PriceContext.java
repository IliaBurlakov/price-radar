package com.priceradar.pricing.domain;

import java.util.Objects;

public final class PriceContext {

    private final String cityName;
    private final long dest;
    private final int spp;

    public PriceContext(String cityName, long dest, int spp) {
        if (cityName == null) {
            throw new NullPointerException("cityName must not be null");
        }
        String normalizedCityName = cityName.trim();
        if (normalizedCityName.isEmpty()) {
            throw new IllegalArgumentException("cityName must not be blank");
        }
        if (normalizedCityName.codePointCount(0, normalizedCityName.length()) > 100) {
            throw new IllegalArgumentException("cityName must not exceed 100 characters");
        }
        if (dest == 0) {
            throw new IllegalArgumentException("dest must not be zero");
        }
        if (spp < 0) {
            throw new IllegalArgumentException("spp must be non-negative");
        }
        this.cityName = normalizedCityName;
        this.dest = dest;
        this.spp = spp;
    }

    public String getCityName() {
        return cityName;
    }

    public long getDest() {
        return dest;
    }

    public int getSpp() {
        return spp;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PriceContext)) {
            return false;
        }
        PriceContext that = (PriceContext) other;
        return dest == that.dest
                && spp == that.spp
                && cityName.equals(that.cityName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cityName, dest, spp);
    }

    @Override
    public String toString() {
        return "PriceContext{" +
                "cityName='" + cityName + '\'' +
                ", dest=" + dest +
                ", spp=" + spp +
                '}';
    }
}
