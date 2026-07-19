package com.priceradar.product.application;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceContext;

import java.time.Instant;

public final class ResolvedQuotePersistenceCommand {

    private final MarketplaceProductDetails product;
    private final long nmId;
    private final String canonicalUrl;
    private final ResolvedVariant resolvedVariant;
    private final PriceContext priceContext;
    private final InterpretedPrice interpretedPrice;
    private final Instant observedAt;

    public ResolvedQuotePersistenceCommand(
            MarketplaceProductDetails product,
            long nmId,
            String canonicalUrl,
            ResolvedVariant resolvedVariant,
            PriceContext priceContext,
            InterpretedPrice interpretedPrice,
            Instant observedAt
    ) {
        if (product == null) {
            throw new IllegalArgumentException("product must not be null");
        }
        if (nmId <= 0) {
            throw new IllegalArgumentException("nmId must be positive");
        }
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            throw new IllegalArgumentException("canonicalUrl must not be blank");
        }
        if (resolvedVariant == null) {
            throw new IllegalArgumentException("resolvedVariant must not be null");
        }
        if (priceContext == null) {
            throw new IllegalArgumentException("priceContext must not be null");
        }
        if (interpretedPrice == null) {
            throw new IllegalArgumentException("interpretedPrice must not be null");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt must not be null");
        }

        this.product = product;
        this.nmId = nmId;
        this.canonicalUrl = canonicalUrl;
        this.resolvedVariant = resolvedVariant;
        this.priceContext = priceContext;
        this.interpretedPrice = interpretedPrice;
        this.observedAt = observedAt;
    }

    public MarketplaceProductDetails getProduct() {
        return product;
    }

    public long getNmId() {
        return nmId;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public ResolvedVariant getResolvedVariant() {
        return resolvedVariant;
    }

    public PriceContext getPriceContext() {
        return priceContext;
    }

    public InterpretedPrice getInterpretedPrice() {
        return interpretedPrice;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
