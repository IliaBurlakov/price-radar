package com.priceradar.product.application;

import java.util.List;
import java.util.Objects;

public final class VariantOption {

    private final String variantKey;
    private final List<VariantAttribute> attributes;
    private final boolean available;
    private final boolean validRegularPrice;

    public VariantOption(
            String variantKey,
            List<VariantAttribute> attributes,
            boolean available,
            boolean validRegularPrice
    ) {
        this.variantKey = VariantKeyValidator.normalize(variantKey);
        this.attributes = copyAttributes(attributes);
        this.available = available;
        this.validRegularPrice = validRegularPrice;
    }

    public static VariantOption wildberriesSize(
            long sizeId,
            List<VariantAttribute> attributes,
            boolean available,
            boolean validRegularPrice
    ) {
        return new VariantOption(
                VariantKeyValidator.forWildberriesSize(sizeId),
                attributes,
                available,
                validRegularPrice
        );
    }

    public static VariantOption providerOption(
            String providerOptionId,
            List<VariantAttribute> attributes,
            boolean available,
            boolean validRegularPrice
    ) {
        return new VariantOption(
                VariantKeyValidator.forProviderOption(providerOptionId),
                attributes,
                available,
                validRegularPrice
        );
    }

    public String getVariantKey() {
        return variantKey;
    }

    public List<VariantAttribute> getAttributes() {
        return attributes;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean hasValidRegularPrice() {
        return validRegularPrice;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VariantOption otherOption)) {
            return false;
        }
        return available == otherOption.available
                && validRegularPrice == otherOption.validRegularPrice
                && variantKey.equals(otherOption.variantKey)
                && attributes.equals(otherOption.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variantKey, attributes, available, validRegularPrice);
    }

    @Override
    public String toString() {
        return "VariantOption{" +
                "variantKey='" + variantKey + '\'' +
                ", attributes=" + attributes +
                ", available=" + available +
                ", validRegularPrice=" + validRegularPrice +
                '}';
    }

    private static List<VariantAttribute> copyAttributes(List<VariantAttribute> attributes) {
        Objects.requireNonNull(attributes, "attributes must not be null");
        if (attributes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("attributes must not contain null values");
        }
        return List.copyOf(attributes);
    }
}
