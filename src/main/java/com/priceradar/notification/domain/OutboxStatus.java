package com.priceradar.notification.domain;

public enum OutboxStatus {
    PENDING,
    RETRY,
    SENT,
    FAILED
}
