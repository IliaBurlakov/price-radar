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
    private final Optional<RubleAmount> notificationReferencePrice;
    private final Optional<Instant> lastProcessedPriceObservedAt;
    private final ThresholdState thresholdState;
    private final Optional<Instant> thresholdObservedAt;
    private final SubscriptionStatus status;
    private final Instant createdAt;
    private final Optional<Instant> endedAt;
    private final long version;

    public Subscription(
            UUID id,
            UUID userId,
            UUID watchTargetId,
            NotificationMode notificationMode,
            Optional<RubleAmount> targetPrice,
            Optional<RubleAmount> notificationReferencePrice,
            Optional<Instant> lastProcessedPriceObservedAt,
            ThresholdState thresholdState,
            Optional<Instant> thresholdObservedAt,
            SubscriptionStatus status,
            Instant createdAt,
            Optional<Instant> endedAt,
            long version
    ) {
        if (id == null || userId == null || watchTargetId == null || notificationMode == null
                || targetPrice == null || notificationReferencePrice == null
                || lastProcessedPriceObservedAt == null
                || thresholdState == null || thresholdObservedAt == null || status == null
                || createdAt == null || endedAt == null) {
            throw new IllegalArgumentException("subscription fields must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("subscription version must be non-negative");
        }
        validatePriceState(
                notificationMode,
                targetPrice,
                notificationReferencePrice,
                lastProcessedPriceObservedAt,
                thresholdState,
                thresholdObservedAt
        );
        validateLifecycle(status, createdAt, endedAt);
        this.id = id;
        this.userId = userId;
        this.watchTargetId = watchTargetId;
        this.notificationMode = notificationMode;
        this.targetPrice = targetPrice;
        this.notificationReferencePrice = notificationReferencePrice;
        this.lastProcessedPriceObservedAt = lastProcessedPriceObservedAt;
        this.thresholdState = thresholdState;
        this.thresholdObservedAt = thresholdObservedAt;
        this.status = status;
        this.createdAt = createdAt;
        this.endedAt = endedAt;
        this.version = version;
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
                notificationReferencePrice,
                lastProcessedPriceObservedAt,
                thresholdState,
                thresholdObservedAt,
                SubscriptionStatus.ENDED,
                createdAt,
                Optional.of(endedAt),
                version
        );
    }

    public Subscription withProcessedRegularPrice(
            RubleAmount referencePrice,
            Instant observedAt
    ) {
        if (notificationMode != NotificationMode.ANY_DECREASE) {
            throw new IllegalStateException(
                    "only ANY_DECREASE subscription has a notification reference"
            );
        }
        if (referencePrice == null || referencePrice.getMinorUnits() == 0) {
            throw new IllegalArgumentException("notification reference price must be positive");
        }
        if (observedAt == null || observedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "processed price observation must belong to subscription period"
            );
        }
        return new Subscription(
                id,
                userId,
                watchTargetId,
                notificationMode,
                targetPrice,
                Optional.of(referencePrice),
                Optional.of(observedAt),
                thresholdState,
                thresholdObservedAt,
                status,
                createdAt,
                endedAt,
                version
        );
    }

    public Subscription withThresholdObservation(ThresholdState newState, Instant observedAt) {
        if (notificationMode != NotificationMode.TARGET_PRICE) {
            throw new IllegalStateException("only TARGET_PRICE subscription has threshold state");
        }
        if (newState == null || newState == ThresholdState.NOT_APPLICABLE) {
            throw new IllegalArgumentException("TARGET_PRICE requires applicable threshold state");
        }
        if (observedAt == null || observedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("threshold observation must belong to subscription period");
        }
        return new Subscription(
                id,
                userId,
                watchTargetId,
                notificationMode,
                targetPrice,
                notificationReferencePrice,
                Optional.of(observedAt),
                newState,
                Optional.of(observedAt),
                status,
                createdAt,
                endedAt,
                version
        );
    }

    public Subscription changeToTargetPrice(
            RubleAmount newTargetPrice,
            Optional<RubleAmount> latestRegularPrice,
            Optional<Instant> latestRegularPriceObservedAt
    ) {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("only active subscription condition can be changed");
        }
        if (newTargetPrice == null || newTargetPrice.getMinorUnits() == 0) {
            throw new IllegalArgumentException("target price must be positive");
        }
        validateObservationPair(latestRegularPrice, latestRegularPriceObservedAt);

        ThresholdState newThresholdState = latestRegularPrice.isPresent()
                ? ThresholdState.ABOVE_TARGET
                : ThresholdState.UNKNOWN;
        Optional<Instant> processedAt = latestInstant(
                lastProcessedPriceObservedAt,
                latestRegularPriceObservedAt
        );
        return new Subscription(
                id,
                userId,
                watchTargetId,
                NotificationMode.TARGET_PRICE,
                Optional.of(newTargetPrice),
                Optional.empty(),
                processedAt,
                newThresholdState,
                latestRegularPriceObservedAt,
                status,
                createdAt,
                endedAt,
                version
        );
    }

    public Subscription changeToAnyDecrease(
            Optional<RubleAmount> historicalMinimum,
            Optional<Instant> latestRegularPriceObservedAt
    ) {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("only active subscription condition can be changed");
        }
        validateObservationPair(historicalMinimum, latestRegularPriceObservedAt);
        return new Subscription(
                id,
                userId,
                watchTargetId,
                NotificationMode.ANY_DECREASE,
                Optional.empty(),
                historicalMinimum,
                latestRegularPriceObservedAt,
                ThresholdState.NOT_APPLICABLE,
                Optional.empty(),
                status,
                createdAt,
                endedAt,
                version
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

    public Optional<RubleAmount> getNotificationReferencePrice() {
        return notificationReferencePrice;
    }

    public Optional<Instant> getLastProcessedPriceObservedAt() {
        return lastProcessedPriceObservedAt;
    }

    public ThresholdState getThresholdState() {
        return thresholdState;
    }

    public Optional<Instant> getThresholdObservedAt() {
        return thresholdObservedAt;
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

    public long getVersion() {
        return version;
    }

    private void validatePriceState(
            NotificationMode mode,
            Optional<RubleAmount> targetPrice,
            Optional<RubleAmount> notificationReferencePrice,
            Optional<Instant> lastProcessedPriceObservedAt,
            ThresholdState thresholdState,
            Optional<Instant> thresholdObservedAt
    ) {
        if (notificationReferencePrice.isPresent() && lastProcessedPriceObservedAt.isEmpty()) {
            throw new IllegalArgumentException(
                    "notification reference requires processed observation time"
            );
        }
        if (notificationReferencePrice.filter(price -> price.getMinorUnits() == 0).isPresent()) {
            throw new IllegalArgumentException("notification reference price must be positive");
        }

        if (mode == NotificationMode.ANY_DECREASE) {
            if (notificationReferencePrice.isPresent() != lastProcessedPriceObservedAt.isPresent()
                    || targetPrice.isPresent() || thresholdState != ThresholdState.NOT_APPLICABLE
                    || thresholdObservedAt.isPresent()) {
                throw new IllegalArgumentException("ANY_DECREASE must not have target threshold state");
            }
            return;
        }

        if (targetPrice.filter(price -> price.getMinorUnits() > 0).isEmpty()) {
            throw new IllegalArgumentException("TARGET_PRICE requires positive target price");
        }
        if (notificationReferencePrice.isPresent()
                || thresholdState == ThresholdState.NOT_APPLICABLE) {
            throw new IllegalArgumentException("TARGET_PRICE has invalid notification state");
        }
        if (thresholdState == ThresholdState.UNKNOWN && thresholdObservedAt.isPresent()) {
            throw new IllegalArgumentException("UNKNOWN threshold state must not have observation time");
        }
        if (thresholdState != ThresholdState.UNKNOWN && thresholdObservedAt.isEmpty()) {
            throw new IllegalArgumentException("resolved threshold state requires observation time");
        }
    }

    private void validateObservationPair(
            Optional<RubleAmount> price,
            Optional<Instant> observedAt
    ) {
        if (price == null || observedAt == null || price.isPresent() != observedAt.isPresent()) {
            throw new IllegalArgumentException("price and observation time must be set together");
        }
        if (price.filter(value -> value.getMinorUnits() == 0).isPresent()) {
            throw new IllegalArgumentException("observed price must be positive");
        }
        if (observedAt.filter(value -> value.isBefore(createdAt)).isPresent()) {
            throw new IllegalArgumentException("observation must belong to subscription statistics period");
        }
    }

    private Optional<Instant> latestInstant(Optional<Instant> first, Optional<Instant> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty() || first.orElseThrow().isAfter(second.orElseThrow())) {
            return first;
        }
        return second;
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
