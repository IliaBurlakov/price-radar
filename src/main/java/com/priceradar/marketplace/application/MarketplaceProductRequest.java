package com.priceradar.marketplace.application;

import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.domain.PriceContext;

public final class MarketplaceProductRequest {
    private final Marketplace marketplace;
    private final String externalProductId;
    private final PriceContext priceContext;

    public MarketplaceProductRequest(Marketplace marketplace, String externalProductId, PriceContext priceContext) {
        if (marketplace == null)
            throw new IllegalArgumentException("marketplace must not be null!");
        if (externalProductId == null)
            throw new IllegalArgumentException("externalProductId must not be null!");
        String normalizedExternalProductId = externalProductId.trim();
        if (normalizedExternalProductId.isBlank())
            throw new IllegalArgumentException("externalProductId must not be blank!");
        if (priceContext == null)
            throw new IllegalArgumentException("priceContext must not be null!");
        this.marketplace = marketplace;
        this.externalProductId = normalizedExternalProductId;
        this.priceContext = priceContext;
    }

    public PriceContext getPriceContext() {
        return priceContext;
    }

    public String getExternalProductId() {
        return externalProductId;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }
}
