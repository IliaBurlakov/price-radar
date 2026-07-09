package com.priceradar.product.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ResolvedVariant {

    private final String variantKey;
    private final List<VariantAttribute> attributes;
    private final boolean autoSelected;

    public ResolvedVariant(
            String variantKey,
            List<VariantAttribute> attributes,
            boolean autoSelected
    ) {
        this.variantKey = VariantKeyValidator.normalize(variantKey);
        this.attributes = copyAttributes(attributes);
        this.autoSelected = autoSelected;
    }

    public static ResolvedVariant fromOption(VariantOption option, boolean autoSelected) {
        Objects.requireNonNull(option, "option must not be null");
        return new ResolvedVariant(option.getVariantKey(), option.getAttributes(), autoSelected);
    }

    public static ResolvedVariant wildberriesSize(
            long sizeId,
            List<VariantAttribute> attributes,
            boolean autoSelected
    ) {
        return new ResolvedVariant(
                VariantKeyValidator.forWildberriesSize(sizeId),
                attributes,
                autoSelected
        );
    }

    public static ResolvedVariant providerOption(
            String providerOptionId,
            List<VariantAttribute> attributes,
            boolean autoSelected
    ) {
        return new ResolvedVariant(
                VariantKeyValidator.forProviderOption(providerOptionId),
                attributes,
                autoSelected
        );
    }

    public static ResolvedVariant noVariant() {
        return new ResolvedVariant(VariantKeyValidator.NO_VARIANT, List.of(), false);
    }

    public String getVariantKey() {
        return variantKey;
    }

    public List<VariantAttribute> getAttributes() {
        return attributes;
    }

    public boolean isAutoSelected() {
        return autoSelected;
    }

    public Optional<String> getDisplayName() {
        if (attributes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(attributes.stream()
                .map(attribute -> attribute.getName() + ": " + attribute.getValue())
                .collect(Collectors.joining(" / ")));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResolvedVariant otherVariant)) {
            return false;
        }
        return autoSelected == otherVariant.autoSelected
                && variantKey.equals(otherVariant.variantKey)
                && attributes.equals(otherVariant.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variantKey, attributes, autoSelected);
    }

    @Override
    public String toString() {
        return "ResolvedVariant{" +
                "variantKey='" + variantKey + '\'' +
                ", attributes=" + attributes +
                ", autoSelected=" + autoSelected +
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
