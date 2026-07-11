package com.priceradar.marketplace.wildberries;

import java.util.Objects;
import java.util.Optional;

public final class WildberriesMappingResult {

    private final WildberriesMappedProduct product;
    private final WildberriesMappingFailure failure;

    private WildberriesMappingResult(WildberriesMappedProduct product, WildberriesMappingFailure failure) {
        this.product = product;
        this.failure = failure;
    }

    public static WildberriesMappingResult success(WildberriesMappedProduct product) {
        return new WildberriesMappingResult(
                Objects.requireNonNull(product, "product must not be null"),
                null
        );
    }

    public static WildberriesMappingResult failure(WildberriesMappingFailure failure) {
        return new WildberriesMappingResult(
                null,
                Objects.requireNonNull(failure, "failure must not be null")
        );
    }

    public boolean isSuccess() {
        return product != null;
    }

    public Optional<WildberriesMappedProduct> getProduct() {
        return Optional.ofNullable(product);
    }

    public Optional<WildberriesMappingFailure> getFailure() {
        return Optional.ofNullable(failure);
    }
}
