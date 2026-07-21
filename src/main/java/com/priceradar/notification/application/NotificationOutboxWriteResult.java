package com.priceradar.notification.application;

public enum NotificationOutboxWriteResult {
    NO_STATE_CHANGE,
    SUBSCRIPTION_INACTIVE,
    STATE_CONFLICT,
    STATE_UPDATED,
    NOTIFICATION_ENQUEUED,
    NOTIFICATION_ALREADY_ENQUEUED
}
