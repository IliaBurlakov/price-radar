package com.priceradar.sharedbasket.application;

import java.util.List;

public final class SharedBasket {

    private final List<SharedBasketItem> items;

    public SharedBasket(List<SharedBasketItem> items) {
        if (items == null || items.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("shared basket items must not contain null values");
        }
        this.items = List.copyOf(items);
    }

    public List<SharedBasketItem> getItems() {
        return items;
    }
}
