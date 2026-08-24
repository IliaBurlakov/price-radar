package com.priceradar.sharedbasket.application;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.pricing.application.ProviderPriceFields;
import com.priceradar.product.application.ResolvedVariant;

import java.time.Instant;
import java.util.Objects;

public final class ResolvedSharedBasketItem {

    private final SharedBasketItem basketItem;
    private final MarketplaceProductDetails product;
    private final ResolvedVariant variant;
    private final ProviderPriceFields priceFields;
    private final Instant observedAt;

    public ResolvedSharedBasketItem(
            SharedBasketItem basketItem,
            MarketplaceProductDetails product,
            ResolvedVariant variant,
            ProviderPriceFields priceFields,
            Instant observedAt
    ) {
        this.basketItem = Objects.requireNonNull(basketItem, "basketItem must not be null");
        this.product = Objects.requireNonNull(product, "product must not be null");
        this.variant = Objects.requireNonNull(variant, "variant must not be null");
        this.priceFields = Objects.requireNonNull(priceFields, "priceFields must not be null");
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
    }

    public SharedBasketItem getBasketItem() { return basketItem; }
    public MarketplaceProductDetails getProduct() { return product; }
    public ResolvedVariant getVariant() { return variant; }
    public ProviderPriceFields getPriceFields() { return priceFields; }
    public Instant getObservedAt() { return observedAt; }
}
