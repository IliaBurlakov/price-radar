package com.priceradar.product.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class VariantResolutionService {

    public Optional<ResolvedVariant> resolveInitial(
            ParsedProductUrl productUrl,
            List<VariantOption> providerOrderedOptions
    ) {
        Objects.requireNonNull(productUrl, "productUrl must not be null");
        Objects.requireNonNull(providerOrderedOptions, "providerOrderedOptions must not be null");
        if (providerOrderedOptions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("providerOrderedOptions must not contain null values");
        }

        if (productUrl.getRequestedSizeId().isPresent()) {
            String expectedVariantKey = VariantKeyValidator.forWildberriesSize(
                    productUrl.getRequestedSizeId().getAsLong()
            );
            return providerOrderedOptions.stream()
                    .filter(option -> option.getVariantKey().equals(expectedVariantKey))
                    .findFirst()
                    .map(option -> ResolvedVariant.fromOption(option, false));
        }

        if (providerOrderedOptions.isEmpty()) {
            return Optional.of(ResolvedVariant.noVariant());
        }

        return providerOrderedOptions.stream()
                .filter(VariantOption::isAvailable)
                .filter(VariantOption::hasValidRegularPrice)
                .filter(option -> !VariantKeyValidator.NO_VARIANT.equals(option.getVariantKey()))
                .findFirst()
                .map(option -> ResolvedVariant.fromOption(option, true));
    }
}
