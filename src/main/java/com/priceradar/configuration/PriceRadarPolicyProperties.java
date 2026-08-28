package com.priceradar.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "priceradar.policy")
public final class PriceRadarPolicyProperties {

    private int activeSubscriptionLimit;
    private int maxProductLinksPerMessage;
    private Duration quoteTtl;
    private Duration pendingActionTtl;
    private Duration sharedBasketImportTtl;
    private int feedbackMaxLength;

    public int getActiveSubscriptionLimit() {
        return activeSubscriptionLimit;
    }

    public void setActiveSubscriptionLimit(int activeSubscriptionLimit) {
        this.activeSubscriptionLimit = positive(activeSubscriptionLimit, "active subscription limit");
    }

    public int getMaxProductLinksPerMessage() {
        return maxProductLinksPerMessage;
    }

    public void setMaxProductLinksPerMessage(int maxProductLinksPerMessage) {
        this.maxProductLinksPerMessage = positive(maxProductLinksPerMessage, "product link limit");
    }

    public Duration getQuoteTtl() {
        return quoteTtl;
    }

    public void setQuoteTtl(Duration quoteTtl) {
        this.quoteTtl = positive(quoteTtl, "quote TTL");
    }

    public Duration getPendingActionTtl() {
        return pendingActionTtl;
    }

    public void setPendingActionTtl(Duration pendingActionTtl) {
        this.pendingActionTtl = positive(pendingActionTtl, "pending action TTL");
    }

    public Duration getSharedBasketImportTtl() {
        return sharedBasketImportTtl;
    }

    public void setSharedBasketImportTtl(Duration sharedBasketImportTtl) {
        this.sharedBasketImportTtl = positive(sharedBasketImportTtl, "shared basket import TTL");
    }

    public int getFeedbackMaxLength() {
        return feedbackMaxLength;
    }

    public void setFeedbackMaxLength(int feedbackMaxLength) {
        if (feedbackMaxLength > 3000) {
            throw new IllegalArgumentException("feedback max length must not exceed database limit 3000");
        }
        this.feedbackMaxLength = positive(feedbackMaxLength, "feedback max length");
    }

    private int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
