package com.priceradar.tracking.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.domain.NotificationMode;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class TrackedSubscriptionItem {

    private final UUID subscriptionId;
    private final long nmId;
    private final Optional<String> title;
    private final Optional<String> brand;
    private final String canonicalUrl;
    private final Optional<String> variantDisplayName;
    private final NotificationMode notificationMode;
    private final Optional<RubleAmount> targetPrice;
    private final Instant trackingStartedAt;
    private final Optional<SnapshotStatus> latestSnapshotStatus;
    private final Optional<RubleAmount> latestRegularPrice;
    private final Optional<Instant> latestObservedAt;

    public TrackedSubscriptionItem(
            UUID subscriptionId,
            long nmId,
            Optional<String> title,
            Optional<String> brand,
            String canonicalUrl,
            Optional<String> variantDisplayName,
            NotificationMode notificationMode,
            Optional<RubleAmount> targetPrice,
            Instant trackingStartedAt,
            Optional<SnapshotStatus> latestSnapshotStatus,
            Optional<RubleAmount> latestRegularPrice,
            Optional<Instant> latestObservedAt
    ) {
        if (subscriptionId == null || notificationMode == null || trackingStartedAt == null) {
            throw new IllegalArgumentException("tracked subscription fields must not be null");
        }
        if (nmId <= 0) {
            throw new IllegalArgumentException("nmId must be positive");
        }
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            throw new IllegalArgumentException("canonicalUrl must not be blank");
        }
        if (title == null || brand == null || variantDisplayName == null || targetPrice == null
                || latestSnapshotStatus == null || latestRegularPrice == null
                || latestObservedAt == null) {
            throw new IllegalArgumentException("tracked subscription optional fields must not be null");
        }
        validateMode(notificationMode, targetPrice);
        validateSnapshot(latestSnapshotStatus, latestRegularPrice, latestObservedAt);
        this.subscriptionId = subscriptionId;
        this.nmId = nmId;
        this.title = normalize(title);
        this.brand = normalize(brand);
        this.canonicalUrl = canonicalUrl.trim();
        this.variantDisplayName = normalize(variantDisplayName);
        this.notificationMode = notificationMode;
        this.targetPrice = targetPrice;
        this.trackingStartedAt = trackingStartedAt;
        this.latestSnapshotStatus = latestSnapshotStatus;
        this.latestRegularPrice = latestRegularPrice;
        this.latestObservedAt = latestObservedAt;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public long getNmId() {
        return nmId;
    }

    public Optional<String> getTitle() {
        return title;
    }

    public Optional<String> getBrand() {
        return brand;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public Optional<String> getVariantDisplayName() {
        return variantDisplayName;
    }

    public NotificationMode getNotificationMode() {
        return notificationMode;
    }

    public Optional<RubleAmount> getTargetPrice() {
        return targetPrice;
    }

    public Instant getTrackingStartedAt() {
        return trackingStartedAt;
    }

    public Optional<SnapshotStatus> getLatestSnapshotStatus() {
        return latestSnapshotStatus;
    }

    public Optional<RubleAmount> getLatestRegularPrice() {
        return latestRegularPrice;
    }

    public Optional<Instant> getLatestObservedAt() {
        return latestObservedAt;
    }

    private void validateMode(
            NotificationMode mode,
            Optional<RubleAmount> targetPrice
    ) {
        if (mode == NotificationMode.ANY_DECREASE && targetPrice.isPresent()) {
            throw new IllegalArgumentException("ANY_DECREASE must not have targetPrice");
        }
        if (mode == NotificationMode.TARGET_PRICE
                && targetPrice.filter(price -> price.getMinorUnits() > 0).isEmpty()) {
            throw new IllegalArgumentException("TARGET_PRICE requires positive targetPrice");
        }
    }

    private void validateSnapshot(
            Optional<SnapshotStatus> status,
            Optional<RubleAmount> regularPrice,
            Optional<Instant> observedAt
    ) {
        if (status.isPresent() != observedAt.isPresent()) {
            throw new IllegalArgumentException("snapshot status and observedAt must be set together");
        }
        if (status.filter(SnapshotStatus.REGULAR_PRICE::equals).isPresent()
                != regularPrice.isPresent()) {
            throw new IllegalArgumentException("REGULAR_PRICE status and regular price must be set together");
        }
    }

    private Optional<String> normalize(Optional<String> value) {
        return value.map(String::trim).filter(text -> !text.isEmpty());
    }
}
