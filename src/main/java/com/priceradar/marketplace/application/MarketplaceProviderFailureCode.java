package com.priceradar.marketplace.application;

public enum MarketplaceProviderFailureCode {
    INVALID_REQUEST,
    PRODUCT_NOT_FOUND,
    VARIANT_NOT_FOUND,
    ACCESS_FORBIDDEN,
    RATE_LIMITED,
    COOLDOWN_ACTIVE,
    SERVER_ERROR,
    TRANSPORT_ERROR,
    MALFORMED_RESPONSE,
    SCHEMA_VIOLATION,
    HTTP_ERROR,
    INTERRUPTED
}
