package com.priceradar.pricing.domain;

import java.util.Objects;

public final class RubleAmount {

    private final long minorUnits;

    private RubleAmount(long minorUnits) {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("minorUnits must be non-negative");
        }
        this.minorUnits = minorUnits;
    }

    public static RubleAmount ofMinorUnits(long minorUnits) {
        return new RubleAmount(minorUnits);
    }

    public long getMinorUnits() {
        return minorUnits;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RubleAmount that)) {
            return false;
        }
        return minorUnits == that.minorUnits;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minorUnits);
    }

    @Override
    public String toString() {
        return "RubleAmount{minorUnits=" + minorUnits + '}';
    }
}
