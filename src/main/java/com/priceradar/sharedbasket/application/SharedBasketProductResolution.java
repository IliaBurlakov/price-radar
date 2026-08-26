package com.priceradar.sharedbasket.application;

import java.util.List;
import java.util.Optional;

public final class SharedBasketProductResolution {

    private final List<ResolvedSharedBasketItem> resolvedItems;
    private final List<UnavailableSharedBasketItem> unavailableItems;
    private final List<UnresolvedSharedBasketItem> unresolvedItems;
    private final Optional<SharedBasketFailure> failure;

    private SharedBasketProductResolution(
            List<ResolvedSharedBasketItem> resolvedItems,
            List<UnavailableSharedBasketItem> unavailableItems,
            List<UnresolvedSharedBasketItem> unresolvedItems,
            Optional<SharedBasketFailure> failure
    ) {
        this.resolvedItems = List.copyOf(resolvedItems);
        this.unavailableItems = List.copyOf(unavailableItems);
        this.unresolvedItems = List.copyOf(unresolvedItems);
        this.failure = failure;
    }

    public static SharedBasketProductResolution success(
            List<ResolvedSharedBasketItem> items,
            List<UnavailableSharedBasketItem> unavailableItems,
            List<UnresolvedSharedBasketItem> unresolvedItems
    ) {
        return new SharedBasketProductResolution(
                items, unavailableItems, unresolvedItems, Optional.empty()
        );
    }

    public static SharedBasketProductResolution failure(SharedBasketFailure failure) {
        return new SharedBasketProductResolution(
                List.of(), List.of(), List.of(), Optional.of(failure)
        );
    }

    public boolean isSuccess() { return failure.isEmpty(); }
    public List<ResolvedSharedBasketItem> getResolvedItems() { return resolvedItems; }
    public List<UnavailableSharedBasketItem> getUnavailableItems() { return unavailableItems; }
    public List<UnresolvedSharedBasketItem> getUnresolvedItems() { return unresolvedItems; }
    public Optional<SharedBasketFailure> getFailure() { return failure; }
}
