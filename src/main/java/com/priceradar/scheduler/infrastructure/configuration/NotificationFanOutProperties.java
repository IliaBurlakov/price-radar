package com.priceradar.scheduler.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "priceradar.notification.fan-out")
public final class NotificationFanOutProperties {

    private int batchSize = 20;
    private Duration claimTimeout = Duration.ofMinutes(2);
    private Duration baseBackoff = Duration.ofSeconds(30);
    private Duration maxBackoff = Duration.ofMinutes(30);

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("fan-out batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    public Duration getClaimTimeout() {
        return claimTimeout;
    }

    public void setClaimTimeout(Duration claimTimeout) {
        this.claimTimeout = positive(claimTimeout, "claimTimeout");
    }

    public Duration getBaseBackoff() {
        return baseBackoff;
    }

    public void setBaseBackoff(Duration baseBackoff) {
        this.baseBackoff = positive(baseBackoff, "baseBackoff");
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = positive(maxBackoff, "maxBackoff");
    }

    private Duration positive(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("fan-out " + fieldName + " must be positive");
        }
        return value;
    }
}
