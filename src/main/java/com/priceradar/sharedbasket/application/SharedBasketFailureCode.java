package com.priceradar.sharedbasket.application;

public enum SharedBasketFailureCode {
    NOT_FOUND,
    RATE_LIMITED,
    TEMPORARILY_UNAVAILABLE,
    MALFORMED_RESPONSE,
    SCHEMA_VIOLATION,
    TRANSPORT_ERROR,
    COOLDOWN_ACTIVE,
    INTERRUPTED
}
