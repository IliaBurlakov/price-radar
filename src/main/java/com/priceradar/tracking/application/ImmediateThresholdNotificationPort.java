package com.priceradar.tracking.application;

import com.priceradar.tracking.domain.Subscription;

import java.time.Instant;

public interface ImmediateThresholdNotificationPort {

    void enqueue(
            Subscription subscription,
            SubscriptionQuoteObservation observation,
            Instant createdAt
    );
}
