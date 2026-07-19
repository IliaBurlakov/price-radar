package com.priceradar.tracking.domain;

import com.priceradar.marketplace.domain.Marketplace;
import java.util.Objects;
import java.util.regex.Pattern;

public final class WatchKey {

    private static final Pattern SIZE_KEY = Pattern.compile("SIZE:[1-9]\\d*");
    private static final String OPTION_PREFIX = "OPTION:";
    private static final String NO_VARIANT_KEY = "NO_VARIANT";

    private final Marketplace marketplace;
    private final long nmId;
    private final String variantKey;
    private final long dest;
    private final int spp;

    public WatchKey(Marketplace marketplace, long nmId, String variantKey, long dest, int spp) {
        this.marketplace = Objects.requireNonNull(marketplace, "marketplace must not be null");
        if (nmId <= 0) {
            throw new IllegalArgumentException("nmId must be positive");
        }
        if (dest == 0) {
            throw new IllegalArgumentException("dest must not be zero");
        }
        if (spp < 0) {
            throw new IllegalArgumentException("spp must be non-negative");
        }
        this.nmId = nmId;
        this.variantKey = normalizeVariantKey(variantKey);
        this.dest = dest;
        this.spp = spp;
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

    public static WatchKey withoutVariant(Marketplace marketplace, long nmId, long dest, int spp) {
        return new WatchKey(marketplace, nmId, NO_VARIANT_KEY, dest, spp);
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public long getNmId() {
        return nmId;
    }

    public String getVariantKey() {
        return variantKey;
    }

    public long getDest() {
        return dest;
    }

    public int getSpp() {
        return spp;
    }

    public VariantKind getVariantKind() {
        if (variantKey.startsWith("SIZE:")) {
            return VariantKind.SIZE;
        }
        if (variantKey.startsWith(OPTION_PREFIX)) {
            return VariantKind.PROVIDER_OPTION;
        }
        return VariantKind.NO_VARIANT;
    }

    public String getVariantValue() {
        if (variantKey.startsWith("SIZE:")) {
            return variantKey.substring("SIZE:".length());
        }
        if (variantKey.startsWith(OPTION_PREFIX)) {
            return variantKey.substring(OPTION_PREFIX.length());
        }
        return NO_VARIANT_KEY;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WatchKey that)) {
            return false;
        }
        return nmId == that.nmId
                && dest == that.dest
                && spp == that.spp
                && marketplace == that.marketplace
                && variantKey.equals(that.variantKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marketplace, nmId, variantKey, dest, spp);
    }

    @Override
    public String toString() {
        return "WatchKey{" +
                "marketplace=" + marketplace +
                ", nmId=" + nmId +
                ", variantKey='" + variantKey + '\'' +
                ", dest=" + dest +
                ", spp=" + spp +
                '}';
    }

    private static String normalizeVariantKey(String variantKey) {
        Objects.requireNonNull(variantKey, "variantKey must not be null");
        String normalized = variantKey.trim();
        if (SIZE_KEY.matcher(normalized).matches() || NO_VARIANT_KEY.equals(normalized)) {
            return normalized;
        }
        if (normalized.startsWith(OPTION_PREFIX)) {
            String optionId = normalized.substring(OPTION_PREFIX.length()).trim();
            if (!optionId.isEmpty()) {
                return OPTION_PREFIX + optionId;
            }
        }
        throw new IllegalArgumentException(
                "variantKey must be SIZE:<positive id>, OPTION:<non-blank id>, or NO_VARIANT"
        );
    }
}
