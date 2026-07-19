package com.priceradar.product.application;

import com.priceradar.marketplace.application.MarketplaceProviderFailure;

import java.util.Optional;

public final class ResolvedQuoteResult {

    public enum FailureCode {
        PROVIDER_FAILURE,
        VARIANT_NOT_RESOLVED,
        PRICE_FIELDS_NOT_FOUND,
        PROVIDER_DATA_MISMATCH
    }

    private final ResolvedQuote quote;
    private final FailureCode failureCode;
    private final String failureMessage;
    private final MarketplaceProviderFailure providerFailure;

    private ResolvedQuoteResult(
            ResolvedQuote quote,
            FailureCode failureCode,
            String failureMessage,
            MarketplaceProviderFailure providerFailure
    ) {
        this.quote = quote;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.providerFailure = providerFailure;
    }

    public static ResolvedQuoteResult success(ResolvedQuote quote) {
        if (quote == null) {
            throw new IllegalArgumentException("quote must not be null");
        }
        return new ResolvedQuoteResult(quote, null, null, null);
    }

    public static ResolvedQuoteResult providerFailure(MarketplaceProviderFailure failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure must not be null");
        }
        return new ResolvedQuoteResult(
                null,
                FailureCode.PROVIDER_FAILURE,
                failure.getMessage(),
                failure
        );
    }

    public static ResolvedQuoteResult failure(FailureCode code, String message) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (code == FailureCode.PROVIDER_FAILURE) {
            throw new IllegalArgumentException("Use providerFailure for provider errors");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return new ResolvedQuoteResult(null, code, message.trim(), null);
    }

    public boolean isSuccess() {
        return quote != null;
    }

    public Optional<ResolvedQuote> getQuote() {
        return Optional.ofNullable(quote);
    }

    public Optional<FailureCode> getFailureCode() {
        return Optional.ofNullable(failureCode);
    }

    public Optional<String> getFailureMessage() {
        return Optional.ofNullable(failureMessage);
    }

    public Optional<MarketplaceProviderFailure> getProviderFailure() {
        return Optional.ofNullable(providerFailure);
    }
}
