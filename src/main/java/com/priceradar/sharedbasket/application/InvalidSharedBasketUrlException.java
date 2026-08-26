package com.priceradar.sharedbasket.application;

public class InvalidSharedBasketUrlException extends IllegalArgumentException {

    public InvalidSharedBasketUrlException(String message) {
        super(message);
    }
}
