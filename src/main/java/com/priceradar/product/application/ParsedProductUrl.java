package com.priceradar.product.application;

import java.util.Objects;
import java.util.OptionalLong;

public final class ParsedProductUrl {

    private final long nmId;
    private final OptionalLong requestedSizeId;

    public ParsedProductUrl(long nmId, OptionalLong requestedSizeId) {
        if (nmId <= 0) {
            throw new IllegalArgumentException("nmId must be positive");
        }
        this.nmId = nmId;

        this.requestedSizeId = Objects.requireNonNull(
                requestedSizeId, "requestedSizeId must not be null"
        );
        if (requestedSizeId.isPresent() && requestedSizeId.getAsLong() <= 0) {
            throw new IllegalArgumentException("requestedSizeId must be positive when present");
        }
    }

    public long getNmId() {
        return nmId;
    }

    public OptionalLong getRequestedSizeId() {
        return requestedSizeId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParsedProductUrl that)) {
            return false;
        }
        return nmId == that.nmId && requestedSizeId.equals(that.requestedSizeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nmId, requestedSizeId);
    }

    @Override
    public String toString() {
        return "ParsedProductUrl{" +
                "nmId=" + nmId +
                ", requestedSizeId=" + requestedSizeId +
                '}';
    }
}
