package com.priceradar.tracking.application;

import com.priceradar.tracking.domain.Subscription;

import java.util.Optional;

public final class SubscriptionEndResult {

    public enum Status {
        ENDED,
        NOT_FOUND
    }

    private final Status status;
    private final Subscription subscription;

    private SubscriptionEndResult(Status status, Subscription subscription) {
        this.status = status;
        this.subscription = subscription;
    }

    public static SubscriptionEndResult ended(Subscription subscription) {
        if (subscription == null) {
            throw new IllegalArgumentException("subscription must not be null");
        }
        return new SubscriptionEndResult(Status.ENDED, subscription);
    }

    public static SubscriptionEndResult notFound() {
        return new SubscriptionEndResult(Status.NOT_FOUND, null);
    }

    public Status getStatus() {
        return status;
    }

    public Optional<Subscription> getSubscription() {
        return Optional.ofNullable(subscription);
    }
}
