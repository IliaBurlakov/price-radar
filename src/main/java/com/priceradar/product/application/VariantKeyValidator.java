package com.priceradar.product.application;

import java.util.Objects;
import java.util.regex.Pattern;

final class VariantKeyValidator {

    static final String OPTION_PREFIX = "OPTION:";
    static final String NO_VARIANT = "NO_VARIANT";

    private static final Pattern SIZE_KEY = Pattern.compile("SIZE:[1-9]\\d*");

    private VariantKeyValidator() {
    }

    static String normalize(String variantKey) {
        Objects.requireNonNull(variantKey, "variantKey must not be null");
        String normalized = variantKey.trim();
        if (SIZE_KEY.matcher(normalized).matches() || NO_VARIANT.equals(normalized)) {
            return normalized;
        }
        if (normalized.startsWith(OPTION_PREFIX)) {
            String providerOptionId = normalized.substring(OPTION_PREFIX.length()).trim();
            if (!providerOptionId.isEmpty()) {
                return OPTION_PREFIX + providerOptionId;
            }
        }
        throw new IllegalArgumentException(
                "variantKey must be SIZE:<positive id>, OPTION:<non-blank id>, or NO_VARIANT"
        );
    }

    static String forWildberriesSize(long sizeId) {
        if (sizeId <= 0) {
            throw new IllegalArgumentException("sizeId must be positive");
        }
        return "SIZE:" + sizeId;
    }

    static String forProviderOption(String providerOptionId) {
        Objects.requireNonNull(providerOptionId, "providerOptionId must not be null");
        return normalize(OPTION_PREFIX + providerOptionId);
    }
}
