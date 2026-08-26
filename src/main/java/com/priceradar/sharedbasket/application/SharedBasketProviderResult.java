package com.priceradar.sharedbasket.application;

import java.util.Optional;

public final class SharedBasketProviderResult {

    private final Optional<SharedBasket> basket;
    private final Optional<SharedBasketFailure> failure;

    private SharedBasketProviderResult(
            Optional<SharedBasket> basket,
            Optional<SharedBasketFailure> failure
    ) {
        this.basket = basket;
        this.failure = failure;
    }

    public static SharedBasketProviderResult success(SharedBasket basket) {
        return new SharedBasketProviderResult(Optional.of(basket), Optional.empty());
    }

    public static SharedBasketProviderResult failure(SharedBasketFailure failure) {
        return new SharedBasketProviderResult(Optional.empty(), Optional.of(failure));
    }

    public boolean isSuccess() {
        return basket.isPresent();
    }

    public Optional<SharedBasket> getBasket() {
        return basket;
    }

    public Optional<SharedBasketFailure> getFailure() {
        return failure;
    }
}
