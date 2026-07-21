package com.priceradar.tracking.domain;

import com.priceradar.pricing.domain.RubleAmount;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class Subscription {

    private final UUID id;
    private final UUID userId;
    private final UUID watchTargetId;
    private final NotificationMode notificationMode;
    private final Optional<RubleAmount> targetPrice;
    private final Optional<RubleAmount> baselinePrice;
    private final Optional<Instant> baselineObservedAt;
    private final ThresholdState thresholdState;
    private final SubscriptionStatus status;
    private final Instant createdAt;
    private final Optional<Instant> endedAt;

    public Subscription(
            UUID id,
            UUID userId,
            UUID watchTargetId,
            NotificationMode notificationMode,
            Optional<RubleAmount> targetPrice,
            Optional<RubleAmount> baselinePrice,
            Optional<Instant> baselineObservedAt,
            ThresholdState thresholdState,
            SubscriptionStatus status,
            Instant createdAt,
            Optional<Instant> endedAt
    ) {
        if (id == null || userId == null || watchTargetId == null || notificationMode == null
                || targetPrice == null || baselinePrice == null || baselineObservedAt == null
                || thresholdState == null || status == null || createdAt == null || endedAt == null) {
            throw new IllegalArgumentException("subscription fields must not be null");
        }
        validatePriceState(notificationMode, targetPrice, baselinePrice, baselineObservedAt, thresholdState);
        validateLifecycle(status, createdAt, endedAt);
        this.id = id;
        this.userId = userId;
        this.watchTargetId = watchTargetId;
        this.notificationMode = notificationMode;
        this.targetPrice = targetPrice;
        this.baselinePrice = baselinePrice;
        this.baselineObservedAt = baselineObservedAt;
        this.thresholdState = thresholdState;
        this.status = status;
        this.createdAt = createdAt;
        this.endedAt = endedAt;
    }

    public Subscription end(Instant endedAt) {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("only active subscription can be ended");
        }
        if (endedAt == null || endedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("endedAt must not be before createdAt");
        }
        return new Subscription(
                id,
                userId,
                watchTargetId,
                notificationMode,
                targetPrice,
                baselinePrice,
                baselineObservedAt,
                thresholdState,
                SubscriptionStatus.ENDED,
                createdAt,
                Optional.of(endedAt)
        );
    }

    public Subscription withBaseline(RubleAmount baselinePrice, Instant observedAt) {
        if (notificationMode != NotificationMode.ANY_DECREASE) {
            throw new IllegalStateException("only ANY_DECREASE subscription has a baseline");
        }
        if (baselinePrice == null || baselinePrice.getMinorUnits() == 0) {
            throw new IllegalArgumentException("baselinePrice must be positive");
        }
        if (observedAt == null || observedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("baseline observation must belong to subscription period");
        }
        return new Subscription(
                id,
                userId,
                watchTargetId,
                notificationMode,
                targetPrice,
                Optional.of(baselinePrice),
                Optional.of(observedAt),
                thresholdState,
                status,
                createdAt,
                endedAt
        );
    }

    public Subscription withThresholdState(ThresholdState newState) {
        if (notificationMode != NotificationMode.TARGET_PRICE) {
            throw new IllegalStateException("only TARGET_PRICE subscription has threshold state");
        }
        if (newState == null || newState == ThresholdState.NOT_APPLICABLE) {
            throw new IllegalArgumentException("TARGET_PRICE requires applicable threshold state");
        }
        return new Subscription(
                id,
                userId,
                watchTargetId,
                notificationMode,
                targetPrice,
                baselinePrice,
                baselineObservedAt,
                newState,
                status,
                createdAt,
                endedAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }

    public NotificationMode getNotificationMode() {
        return notificationMode;
    }

    public Optional<RubleAmount> getTargetPrice() {
        return targetPrice;
    }

    public Optional<RubleAmount> getBaselinePrice() {
        return baselinePrice;
    }

    public Optional<Instant> getBaselineObservedAt() {
        return baselineObservedAt;
    }

    public ThresholdState getThresholdState() {
        return thresholdState;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Optional<Instant> getEndedAt() {
        return endedAt;
    }

    private void validatePriceState(
            NotificationMode mode,
            Optional<RubleAmount> targetPrice,
            Optional<RubleAmount> baselinePrice,
            Optional<Instant> baselineObservedAt,
            ThresholdState thresholdState
    ) {
        if (baselinePrice.isPresent() != baselineObservedAt.isPresent()) {
            throw new IllegalArgumentException("baseline price and observation time must be set together");
        }
        if (baselinePrice.filter(price -> price.getMinorUnits() == 0).isPresent()) {
            throw new IllegalArgumentException("baseline price must be positive");
        }

        if (mode == NotificationMode.ANY_DECREASE) {
            if (targetPrice.isPresent() || thresholdState != ThresholdState.NOT_APPLICABLE) {
                throw new IllegalArgumentException("ANY_DECREASE must not have target threshold state");
            }
            return;
        }

        if (targetPrice.filter(price -> price.getMinorUnits() > 0).isEmpty()) {
            throw new IllegalArgumentException("TARGET_PRICE requires positive target price");
        }
        if (baselinePrice.isPresent() || thresholdState == ThresholdState.NOT_APPLICABLE) {
            throw new IllegalArgumentException("TARGET_PRICE has invalid notification state");
        }
    }

    private void validateLifecycle(
            SubscriptionStatus status,
            Instant createdAt,
            Optional<Instant> endedAt
    ) {
        if (status == SubscriptionStatus.ACTIVE && endedAt.isPresent()) {
            throw new IllegalArgumentException("active subscription must not have endedAt");
        }
        if (status == SubscriptionStatus.ENDED && endedAt.isEmpty()) {
            throw new IllegalArgumentException("ended subscription requires endedAt");
        }
        if (endedAt.filter(value -> value.isBefore(createdAt)).isPresent()) {
            throw new IllegalArgumentException("endedAt must not be before createdAt");
        }
    }
}
