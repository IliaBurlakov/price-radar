package com.priceradar.marketplace.wildberries;

import java.util.Objects;

public final class WildberriesMappingFailure {

    private final WildberriesMappingFailureCode code;
    private final String message;

    public WildberriesMappingFailure(WildberriesMappingFailureCode code, String message) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = requireText(message, "message");
    }

    public WildberriesMappingFailureCode getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WildberriesMappingFailure that)) {
            return false;
        }
        return code == that.code && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message);
    }

    @Override
    public String toString() {
        return "WildberriesMappingFailure{" +
                "code=" + code +
                ", message='" + message + '\'' +
                '}';
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
