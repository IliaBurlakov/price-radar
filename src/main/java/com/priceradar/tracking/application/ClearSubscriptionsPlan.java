package com.priceradar.tracking.application;

public final class ClearSubscriptionsPlan {

    private final int subscriptionCount;
    private final String fingerprint;

    public ClearSubscriptionsPlan(int subscriptionCount, String fingerprint) {
        if (subscriptionCount < 0 || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("clear subscriptions plan is invalid");
        }
        this.subscriptionCount = subscriptionCount;
        this.fingerprint = fingerprint;
    }

    public int getSubscriptionCount() { return subscriptionCount; }
    public String getFingerprint() { return fingerprint; }
    public boolean isEmpty() { return subscriptionCount == 0; }
}
