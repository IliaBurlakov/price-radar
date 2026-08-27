package com.priceradar.tracking.application;

import com.priceradar.tracking.domain.Subscription;

import java.util.Optional;

public final class SubscriptionConditionChangeResult {

    public enum Status {
        CHANGED,
        UNCHANGED,
        NOT_FOUND,
        CONFLICT
    }

    private final Status status;
    private final Optional<Subscription> subscription;

    private SubscriptionConditionChangeResult(Status status, Optional<Subscription> subscription) {
        this.status = status;
        this.subscription = subscription;
    }

    public static SubscriptionConditionChangeResult changed(Subscription subscription) {
        return new SubscriptionConditionChangeResult(Status.CHANGED, Optional.of(subscription));
    }

    public static SubscriptionConditionChangeResult unchanged(Subscription subscription) {
        return new SubscriptionConditionChangeResult(Status.UNCHANGED, Optional.of(subscription));
    }

    public static SubscriptionConditionChangeResult of(Status status) {
        if (status == Status.CHANGED || status == Status.UNCHANGED) {
            throw new IllegalArgumentException("successful result requires subscription");
        }
        return new SubscriptionConditionChangeResult(status, Optional.empty());
    }

    public Status getStatus() {
        return status;
    }

    public Optional<Subscription> getSubscription() {
        return subscription;
    }
}
