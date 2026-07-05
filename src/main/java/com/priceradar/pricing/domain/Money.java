package com.priceradar.pricing.domain;

public record Money(long minorUnits) {

    public Money {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("minorUnits must be non-negative");
        }
    }

    public static Money ofRubMinor(long minorUnits) {
        return new Money(minorUnits);
    }

    public String currencyCode() {
        return "RUB";
    }
}