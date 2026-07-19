package com.priceradar.marketplace.application;

import java.time.Instant;
import java.util.Optional;

public final class MarketplaceProviderFailure {
    private final MarketplaceProviderFailureCode code;
    private final String message;
    private final Optional<Instant> retryNotBefore;
    private final String correlationId;

    public MarketplaceProviderFailure(
            MarketplaceProviderFailureCode code,
            String message,
            Optional<Instant> retryNotBefore,
            String correlationId
    ) {
        if (code == null)
            throw new IllegalArgumentException("error code must not be null!");
        if (message == null)
            throw new IllegalArgumentException("message must not be null!");
        String normalizedMessage = message.trim();
        if (normalizedMessage.isBlank())
            throw new IllegalArgumentException("message must not be blank!");
        if (retryNotBefore == null)
            throw new IllegalArgumentException("retryNotBefore must not be null!");
        if (correlationId == null)
            throw new IllegalArgumentException("correlationId must not be null!");
        String normalizedCorrelationId = correlationId.trim();
        if (normalizedCorrelationId.isBlank())
            throw new IllegalArgumentException("correlationId must not be blank!");
        this.code = code;
        this.message = normalizedMessage;
        this.retryNotBefore = retryNotBefore;
        this.correlationId = normalizedCorrelationId;
    }

    public String getMessage() {
        return message;
    }

    public MarketplaceProviderFailureCode getCode() {
        return code;
    }

    public Optional<Instant> getRetryNotBefore() {
        return retryNotBefore;
    }

    public String getCorrelationId() {
        return correlationId;
    }

}
