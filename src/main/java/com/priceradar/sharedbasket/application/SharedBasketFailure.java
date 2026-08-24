package com.priceradar.sharedbasket.application;

import java.util.Objects;

public final class SharedBasketFailure {

    private final SharedBasketFailureCode code;
    private final String message;

    public SharedBasketFailure(SharedBasketFailureCode code, String message) {
        this.code = Objects.requireNonNull(code, "shared basket failure code must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("shared basket failure message must not be blank");
        }
        this.message = message.trim();
    }

    public SharedBasketFailureCode getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
