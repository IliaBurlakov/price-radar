package com.priceradar.sharedbasket.application;

import java.util.List;
import java.util.Optional;

public final class SharedBasketProductResolution {

    private final List<ResolvedSharedBasketItem> resolvedItems;
    private final int skippedItems;
    private final Optional<SharedBasketFailure> failure;

    private SharedBasketProductResolution(
            List<ResolvedSharedBasketItem> resolvedItems,
            int skippedItems,
            Optional<SharedBasketFailure> failure
    ) {
        this.resolvedItems = List.copyOf(resolvedItems);
        this.skippedItems = skippedItems;
        this.failure = failure;
    }

    public static SharedBasketProductResolution success(
            List<ResolvedSharedBasketItem> items,
            int skippedItems
    ) {
        return new SharedBasketProductResolution(items, skippedItems, Optional.empty());
    }

    public static SharedBasketProductResolution failure(SharedBasketFailure failure) {
        return new SharedBasketProductResolution(List.of(), 0, Optional.of(failure));
    }

    public boolean isSuccess() { return failure.isEmpty(); }
    public List<ResolvedSharedBasketItem> getResolvedItems() { return resolvedItems; }
    public int getSkippedItems() { return skippedItems; }
    public Optional<SharedBasketFailure> getFailure() { return failure; }
}
