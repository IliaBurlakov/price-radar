package com.priceradar.tracking.domain;

import com.priceradar.marketplace.domain.Marketplace;
import java.util.Objects;
import java.util.regex.Pattern;

public record WatchKey(
        Marketplace marketplace,
        long nmId,
        String variantKey,
        long dest,
        int spp
) {

    private static final Pattern SIZE_KEY = Pattern.compile("SIZE:[1-9]\\d*");
    private static final String OPTION_PREFIX = "OPTION:";
    private static final String NO_SIZE_KEY = "NO_SIZE";

    public WatchKey {
        Objects.requireNonNull(marketplace, "marketplace must not be null");
        if (nmId <= 0) {
            throw new IllegalArgumentException("nmId must be positive");
        }
        variantKey = normalizeVariantKey(variantKey);
        if (dest == 0) {
            throw new IllegalArgumentException("dest must not be zero");
        }
        if (spp < 0 || spp > 100) {
            throw new IllegalArgumentException("spp must be between 0 and 100");
        }
    }

    public static WatchKey forSize(Marketplace marketplace, long nmId, long sizeId, long dest, int spp) {
        if (sizeId <= 0) {
            throw new IllegalArgumentException("sizeId must be positive");
        }
        return new WatchKey(marketplace, nmId, "SIZE:" + sizeId, dest, spp);
    }

    public static WatchKey forProviderOption(
            Marketplace marketplace,
            long nmId,
            String providerOptionId,
            long dest,
            int spp
    ) {
        Objects.requireNonNull(providerOptionId, "providerOptionId must not be null");
        return new WatchKey(marketplace, nmId, OPTION_PREFIX + providerOptionId, dest, spp);
    }

    public static WatchKey withoutSize(Marketplace marketplace, long nmId, long dest, int spp) {
        return new WatchKey(marketplace, nmId, NO_SIZE_KEY, dest, spp);
    }

    public VariantKind variantKind() {
        if (variantKey.startsWith("SIZE:")) {
            return VariantKind.SIZE;
        }
        if (variantKey.startsWith(OPTION_PREFIX)) {
            return VariantKind.PROVIDER_OPTION;
        }
        return VariantKind.NO_SIZE;
    }

    private static String normalizeVariantKey(String variantKey) {
        Objects.requireNonNull(variantKey, "variantKey must not be null");
        String normalized = variantKey.trim();
        if (SIZE_KEY.matcher(normalized).matches() || NO_SIZE_KEY.equals(normalized)) {
            return normalized;
        }
        if (normalized.startsWith(OPTION_PREFIX)) {
            String optionId = normalized.substring(OPTION_PREFIX.length()).trim();
            if (!optionId.isEmpty()) {
                return OPTION_PREFIX + optionId;
            }
        }
        throw new IllegalArgumentException(
                "variantKey must be SIZE:<positive id>, OPTION:<non-blank id>, or NO_SIZE"
        );
    }
}
