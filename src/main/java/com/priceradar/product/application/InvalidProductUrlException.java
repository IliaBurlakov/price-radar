package com.priceradar.product.application;

public final class InvalidProductUrlException extends IllegalArgumentException {

    private final Reason reason;

    public InvalidProductUrlException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        EMPTY,
        MALFORMED,
        UNSUPPORTED_URL,
        INVALID_PRODUCT_ID,
        INVALID_SIZE_ID
    }
}
