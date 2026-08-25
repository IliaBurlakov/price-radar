package com.priceradar.tracking.application;

import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.ThresholdState;

import java.util.Optional;

public final class SubscriptionCreationResult {

    public enum Status {
        CREATED,
        ALREADY_ACTIVE,
        LIMIT_REACHED,
        QUOTE_EXPIRED,
        REGION_MISMATCH,
        USER_NOT_FOUND,
        WATCH_TARGET_NOT_FOUND
    }

    private final Status status;
    private final Subscription subscription;

    private SubscriptionCreationResult(Status status, Subscription subscription) {
        this.status = status;
        this.subscription = subscription;
    }

    public static SubscriptionCreationResult created(Subscription subscription) {
        return withSubscription(Status.CREATED, subscription);
    }

    public static SubscriptionCreationResult alreadyActive(Subscription subscription) {
        return withSubscription(Status.ALREADY_ACTIVE, subscription);
    }

    public static SubscriptionCreationResult failed(Status status) {
        if (status == null || status == Status.CREATED || status == Status.ALREADY_ACTIVE) {
            throw new IllegalArgumentException("failure status is required");
        }
        return new SubscriptionCreationResult(status, null);
    }

    public Status getStatus() {
        return status;
    }

    public Optional<Subscription> getSubscription() {
        return Optional.ofNullable(subscription);
    }

    public boolean isCreated() {
        return status == Status.CREATED;
    }

    public boolean isTargetAlreadyReached() {
        return subscription != null
                && subscription.getThresholdState()
                == ThresholdState.REACHED_NOTIFIED;
    }

    private static SubscriptionCreationResult withSubscription(
            Status status,
            Subscription subscription
    ) {
        if (subscription == null) {
            throw new IllegalArgumentException("subscription must not be null");
        }
        return new SubscriptionCreationResult(status, subscription);
    }
}
