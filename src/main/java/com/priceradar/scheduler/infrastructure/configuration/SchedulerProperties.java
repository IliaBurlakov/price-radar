package com.priceradar.scheduler.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "priceradar.scheduler")
public final class SchedulerProperties {

    private boolean enabled = true;
    private int batchSize = 20;
    private Duration refreshInterval = Duration.ofHours(6);
    private Duration maxJitter = Duration.ofMinutes(30);
    private Duration failureRetryDelay = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = positive(refreshInterval, "refreshInterval");
    }

    public Duration getMaxJitter() {
        return maxJitter;
    }

    public void setMaxJitter(Duration maxJitter) {
        if (maxJitter == null || maxJitter.isNegative()) {
            throw new IllegalArgumentException("maxJitter must be non-negative");
        }
        this.maxJitter = maxJitter;
    }

    public Duration getFailureRetryDelay() {
        return failureRetryDelay;
    }

    public void setFailureRetryDelay(Duration failureRetryDelay) {
        this.failureRetryDelay = positive(failureRetryDelay, "failureRetryDelay");
    }

    private Duration positive(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
