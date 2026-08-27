package com.priceradar.product.application;

import com.priceradar.marketplace.application.MarketplaceProviderFailure;

import java.util.List;
import java.util.Optional;

public final class BatchResolvedQuoteResult {

    private final List<BatchResolvedQuoteItem> itemResults;
    private final Optional<MarketplaceProviderFailure> failure;

    private BatchResolvedQuoteResult(
            List<BatchResolvedQuoteItem> itemResults,
            Optional<MarketplaceProviderFailure> failure
    ) {
        this.itemResults = List.copyOf(itemResults);
        this.failure = failure;
    }

    public static BatchResolvedQuoteResult success(List<BatchResolvedQuoteItem> itemResults) {
        return new BatchResolvedQuoteResult(itemResults, Optional.empty());
    }

    public static BatchResolvedQuoteResult failure(MarketplaceProviderFailure failure) {
        return new BatchResolvedQuoteResult(List.of(), Optional.of(failure));
    }

    public boolean isSuccess() {
        return failure.isEmpty();
    }

    public List<BatchResolvedQuoteItem> getItemResults() {
        return itemResults;
    }

    public Optional<MarketplaceProviderFailure> getFailure() {
        return failure;
    }
}
