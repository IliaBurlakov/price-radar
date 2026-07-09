package com.priceradar.product.application;

import java.util.Objects;

public final class VariantAttribute {

    private final String name;
    private final String value;

    public VariantAttribute(String name, String value) {
        this.name = requireText(name, "name");
        this.value = requireText(value, "value");
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VariantAttribute otherAttribute)) {
            return false;
        }
        return name.equals(otherAttribute.name) && value.equals(otherAttribute.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return "VariantAttribute{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    private static String requireText(String text, String fieldName) {
        if (text == null) {
            throw new NullPointerException(fieldName + " must not be null");
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
