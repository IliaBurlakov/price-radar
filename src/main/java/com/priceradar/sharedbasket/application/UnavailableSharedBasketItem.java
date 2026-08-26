package com.priceradar.sharedbasket.application;

import java.util.Objects;

public final class UnavailableSharedBasketItem {

    private final SharedBasketItem basketItem;
    private final String displayName;

    public UnavailableSharedBasketItem(SharedBasketItem basketItem, String displayName) {
        this.basketItem = Objects.requireNonNull(basketItem, "basketItem must not be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.displayName = displayName.trim();
    }

    public SharedBasketItem getBasketItem() {
        return basketItem;
    }

    public String getDisplayName() {
        return displayName;
    }
}
