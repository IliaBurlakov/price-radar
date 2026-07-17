package com.priceradar.notification.application;

import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.domain.RubleAmount;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class NotificationIntent {

    private final UUID subscriptionId;
    private final UUID snapshotId;
    private final NotificationType type;
    private final Optional<RubleAmount> previousPrice;
    private final RubleAmount currentPrice;
    private final Instant observedAt;

    public NotificationIntent(
            UUID subscriptionId,
            UUID snapshotId,
            NotificationType type,
            Optional<RubleAmount> previousPrice,
            RubleAmount currentPrice,
            Instant observedAt
    ) {
        if (subscriptionId == null || snapshotId == null || type == null || previousPrice == null
                || currentPrice == null || observedAt == null) {
            throw new IllegalArgumentException("notification intent fields must not be null");
        }
        validatePrices(type, previousPrice, currentPrice);
        this.subscriptionId = subscriptionId;
        this.snapshotId = snapshotId;
        this.type = type;
        this.previousPrice = previousPrice;
        this.currentPrice = currentPrice;
        this.observedAt = observedAt;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public NotificationType getType() {
        return type;
    }

    public Optional<RubleAmount> getPreviousPrice() {
        return previousPrice;
    }

    public RubleAmount getCurrentPrice() {
        return currentPrice;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    private void validatePrices(
            NotificationType type,
            Optional<RubleAmount> previousPrice,
            RubleAmount currentPrice
    ) {
        if (currentPrice.getMinorUnits() == 0) {
            throw new IllegalArgumentException("currentPrice must be positive");
        }
        if (type == NotificationType.PRICE_DECREASE) {
            RubleAmount previous = previousPrice.orElseThrow(() ->
                    new IllegalArgumentException("PRICE_DECREASE requires previousPrice"));
            if (currentPrice.getMinorUnits() >= previous.getMinorUnits()) {
                throw new IllegalArgumentException("PRICE_DECREASE requires a lower currentPrice");
            }
            return;
        }
        if (previousPrice.isPresent()) {
            throw new IllegalArgumentException("TARGET_REACHED must not have previousPrice");
        }
    }
}
