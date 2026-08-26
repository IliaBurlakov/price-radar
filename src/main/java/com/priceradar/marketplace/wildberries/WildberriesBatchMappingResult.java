package com.priceradar.marketplace.wildberries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class WildberriesBatchMappingResult {

    private final Map<Long, WildberriesMappingResult> products;
    private final Optional<WildberriesMappingFailure> failure;

    private WildberriesBatchMappingResult(
            Map<Long, WildberriesMappingResult> products,
            Optional<WildberriesMappingFailure> failure
    ) {
        this.products = Map.copyOf(new LinkedHashMap<>(products));
        this.failure = failure;
    }

    public static WildberriesBatchMappingResult success(Map<Long, WildberriesMappingResult> products) {
        return new WildberriesBatchMappingResult(products, Optional.empty());
    }

    public static WildberriesBatchMappingResult failure(WildberriesMappingFailure failure) {
        return new WildberriesBatchMappingResult(Map.of(), Optional.of(failure));
    }

    public boolean isSuccess() { return failure.isEmpty(); }
    public Map<Long, WildberriesMappingResult> getProducts() { return products; }
    public Optional<WildberriesMappingFailure> getFailure() { return failure; }
}
