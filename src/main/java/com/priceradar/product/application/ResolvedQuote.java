package com.priceradar.product.application;

import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceContext;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class ResolvedQuote {

    private final UUID watchTargetId;
    private final UUID quoteSnapshotId;
    private final Marketplace marketplace;
    private final long nmId;
    private final String canonicalUrl;
    private final Optional<String> title;
    private final Optional<String> brand;
    private final ResolvedVariant resolvedVariant;
    private final InterpretedPrice interpretedPrice;
    private final PriceContext priceContext;
    private final Instant observedAt;
    private final Instant expiresAt;

    public ResolvedQuote(
            UUID watchTargetId,
            UUID quoteSnapshotId,
            Marketplace marketplace,
            long nmId,
            String canonicalUrl,
            Optional<String> title,
            Optional<String> brand,
            ResolvedVariant resolvedVariant,
            InterpretedPrice interpretedPrice,
            PriceContext priceContext,
            Instant observedAt,
            Instant expiresAt
    ) {
        if (watchTargetId == null || quoteSnapshotId == null || marketplace == null || resolvedVariant == null
                || interpretedPrice == null || priceContext == null || observedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("resolved quote fields must not be null");
        }
        if (nmId <= 0) {
            throw new IllegalArgumentException("nmId must be positive");
        }
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            throw new IllegalArgumentException("canonicalUrl must not be blank");
        }
        if (title == null || brand == null) {
            throw new IllegalArgumentException("title and brand must not be null");
        }
        if (expiresAt.isBefore(observedAt)) {
            throw new IllegalArgumentException("expiresAt must not be before observedAt");
        }

        this.watchTargetId = watchTargetId;
        this.quoteSnapshotId = quoteSnapshotId;
        this.marketplace = marketplace;
        this.nmId = nmId;
        this.canonicalUrl = canonicalUrl;
        this.title = title;
        this.brand = brand;
        this.resolvedVariant = resolvedVariant;
        this.interpretedPrice = interpretedPrice;
        this.priceContext = priceContext;
        this.observedAt = observedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }

    public UUID getQuoteSnapshotId() {
        return quoteSnapshotId;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public long getNmId() {
        return nmId;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public Optional<String> getTitle() {
        return title;
    }

    public Optional<String> getBrand() {
        return brand;
    }

    public ResolvedVariant getResolvedVariant() {
        return resolvedVariant;
    }

    public InterpretedPrice getInterpretedPrice() {
        return interpretedPrice;
    }

    public PriceContext getPriceContext() {
        return priceContext;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
