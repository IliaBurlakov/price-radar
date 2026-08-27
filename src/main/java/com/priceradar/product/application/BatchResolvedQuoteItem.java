package com.priceradar.product.application;

import java.util.Optional;

public final class BatchResolvedQuoteItem {

    private final ParsedProductUrl parsedUrl;
    private final ResolvedQuoteResult result;
    private final Optional<String> productTitle;

    public BatchResolvedQuoteItem(
            ParsedProductUrl parsedUrl,
            ResolvedQuoteResult result,
            Optional<String> productTitle
    ) {
        if (parsedUrl == null || result == null || productTitle == null) {
            throw new IllegalArgumentException("batch quote item fields must not be null");
        }
        this.parsedUrl = parsedUrl;
        this.result = result;
        this.productTitle = productTitle;
    }

    public ParsedProductUrl getParsedUrl() { return parsedUrl; }
    public ResolvedQuoteResult getResult() { return result; }
    public Optional<String> getProductTitle() { return productTitle; }
}
