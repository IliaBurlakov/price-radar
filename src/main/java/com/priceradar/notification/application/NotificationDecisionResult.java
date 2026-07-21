package com.priceradar.notification.application;

import com.priceradar.tracking.domain.Subscription;

import java.util.Optional;

public final class NotificationDecisionResult {

    private final Subscription subscription;
    private final Optional<NotificationIntent> notificationIntent;
    private final boolean stateChanged;

    private NotificationDecisionResult(
            Subscription subscription,
            Optional<NotificationIntent> notificationIntent,
            boolean stateChanged
    ) {
        if (subscription == null || notificationIntent == null) {
            throw new IllegalArgumentException("notification decision fields must not be null");
        }
        if (notificationIntent.isPresent() && !stateChanged) {
            throw new IllegalArgumentException("notification requires a state transition");
        }
        this.subscription = subscription;
        this.notificationIntent = notificationIntent;
        this.stateChanged = stateChanged;
    }

    public static NotificationDecisionResult unchanged(Subscription subscription) {
        return new NotificationDecisionResult(subscription, Optional.empty(), false);
    }

    public static NotificationDecisionResult changed(Subscription subscription) {
        return new NotificationDecisionResult(subscription, Optional.empty(), true);
    }

    public static NotificationDecisionResult notify(
            Subscription subscription,
            NotificationIntent notificationIntent
    ) {
        return new NotificationDecisionResult(
                subscription,
                Optional.of(notificationIntent),
                true
        );
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public Optional<NotificationIntent> getNotificationIntent() {
        return notificationIntent;
    }

    public boolean isStateChanged() {
        return stateChanged;
    }
}
