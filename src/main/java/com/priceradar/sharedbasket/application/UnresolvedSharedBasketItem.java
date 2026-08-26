package com.priceradar.sharedbasket.application;

import java.util.Objects;

public final class UnresolvedSharedBasketItem {

    public enum Reason {
        PRODUCT_NOT_RETURNED,
        VARIANT_NOT_FOUND,
        MAPPING_FAILED
    }

    private final SharedBasketItem basketItem;
    private final Reason reason;

    public UnresolvedSharedBasketItem(SharedBasketItem basketItem, Reason reason) {
        this.basketItem = Objects.requireNonNull(basketItem, "basketItem must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public SharedBasketItem getBasketItem() {
        return basketItem;
    }

    public Reason getReason() {
        return reason;
    }
}
