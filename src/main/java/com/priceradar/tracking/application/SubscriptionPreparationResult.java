package com.priceradar.tracking.application;

import com.priceradar.tracking.domain.Subscription;

import java.util.Optional;

public final class SubscriptionPreparationResult {

    public enum Status {
        READY,
        ALREADY_ACTIVE,
        LIMIT_REACHED,
        QUOTE_EXPIRED,
        USER_NOT_FOUND,
        WATCH_TARGET_NOT_FOUND
    }

    private final Status status;
    private final Subscription existingSubscription;

    private SubscriptionPreparationResult(Status status, Subscription existingSubscription) {
        this.status = status;
        this.existingSubscription = existingSubscription;
    }

    public static SubscriptionPreparationResult ready() {
        return new SubscriptionPreparationResult(Status.READY, null);
    }

    public static SubscriptionPreparationResult alreadyActive(Subscription subscription) {
        if (subscription == null) {
            throw new IllegalArgumentException("subscription must not be null");
        }
        return new SubscriptionPreparationResult(Status.ALREADY_ACTIVE, subscription);
    }

    public static SubscriptionPreparationResult failed(Status status) {
        if (status == null || status == Status.READY || status == Status.ALREADY_ACTIVE) {
            throw new IllegalArgumentException("failure status is required");
        }
        return new SubscriptionPreparationResult(status, null);
    }

    public Status getStatus() {
        return status;
    }

    public Optional<Subscription> getExistingSubscription() {
        return Optional.ofNullable(existingSubscription);
    }

    public boolean isReady() {
        return status == Status.READY;
    }
}
