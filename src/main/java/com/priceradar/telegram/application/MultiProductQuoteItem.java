package com.priceradar.telegram.application;

import com.priceradar.product.application.ResolvedQuote;

import java.util.Optional;

public final class MultiProductQuoteItem {

    public enum Status {
        AVAILABLE,
        UNAVAILABLE,
        UNRESOLVED
    }

    private final int position;
    private final long nmId;
    private final String displayName;
    private final Status status;
    private final Optional<ResolvedQuote> quote;

    public MultiProductQuoteItem(
            int position,
            long nmId,
            String displayName,
            Status status,
            Optional<ResolvedQuote> quote
    ) {
        if (position < 0 || nmId <= 0 || displayName == null || displayName.isBlank()
                || status == null || quote == null) {
            throw new IllegalArgumentException("multi-product quote item is invalid");
        }
        if (status == Status.UNRESOLVED && quote.isPresent()) {
            throw new IllegalArgumentException("unresolved item must not contain a quote");
        }
        if (status == Status.AVAILABLE && quote.isEmpty()) {
            throw new IllegalArgumentException("available item must contain a quote");
        }
        this.position = position;
        this.nmId = nmId;
        this.displayName = displayName.trim();
        this.status = status;
        this.quote = quote;
    }

    public int getPosition() { return position; }
    public long getNmId() { return nmId; }
    public String getDisplayName() { return displayName; }
    public Status getStatus() { return status; }
    public Optional<ResolvedQuote> getQuote() { return quote; }
}
