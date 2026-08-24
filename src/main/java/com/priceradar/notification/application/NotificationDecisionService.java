package com.priceradar.notification.application;

import com.priceradar.notification.domain.NotificationType;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;

import java.util.Optional;

public final class NotificationDecisionService {

    public NotificationDecisionResult evaluate(
            Subscription subscription,
            NotificationObservation observation
    ) {
        validateInput(subscription, observation);

        if (!observation.getPrice().hasValidRegularPrice()) {
            return NotificationDecisionResult.unchanged(subscription);
        }

        RubleAmount currentPrice = observation.getPrice().getRegularPrice().orElseThrow();
        if (subscription.getNotificationMode() == NotificationMode.ANY_DECREASE) {
            return evaluateAnyDecrease(subscription, observation, currentPrice);
        }
        return evaluateTargetPrice(subscription, observation, currentPrice);
    }

    private NotificationDecisionResult evaluateAnyDecrease(
            Subscription subscription,
            NotificationObservation observation,
            RubleAmount currentPrice
    ) {
        if (subscription.getLastProcessedPriceObservedAt()
                .filter(previous -> !observation.getObservedAt().isAfter(previous))
                .isPresent()) {
            return NotificationDecisionResult.unchanged(subscription);
        }
        Optional<RubleAmount> referencePrice = subscription.getNotificationReferencePrice();
        RubleAmount updatedReference = referencePrice
                .filter(reference -> currentPrice.getMinorUnits() >= reference.getMinorUnits())
                .orElse(currentPrice);
        Subscription updated = subscription.withProcessedRegularPrice(
                updatedReference,
                observation.getObservedAt()
        );
        if (referencePrice.isEmpty()
                || currentPrice.getMinorUnits() >= referencePrice.orElseThrow().getMinorUnits()) {
            return NotificationDecisionResult.changed(updated);
        }

        NotificationIntent intent = new NotificationIntent(
                subscription.getId(),
                observation.getSnapshotId(),
                NotificationType.PRICE_DECREASE,
                referencePrice,
                currentPrice,
                observation.getObservedAt()
        );
        return NotificationDecisionResult.notify(updated, intent);
    }

    private NotificationDecisionResult evaluateTargetPrice(
            Subscription subscription,
            NotificationObservation observation,
            RubleAmount currentPrice
    ) {
        if (subscription.getThresholdObservedAt()
                .filter(previous -> !observation.getObservedAt().isAfter(previous))
                .isPresent()) {
            return NotificationDecisionResult.unchanged(subscription);
        }
        RubleAmount targetPrice = subscription.getTargetPrice().orElseThrow();
        boolean targetReached = currentPrice.getMinorUnits() <= targetPrice.getMinorUnits();
        ThresholdState currentState = subscription.getThresholdState();

        if (!targetReached) {
            return NotificationDecisionResult.changed(
                    subscription.withThresholdObservation(
                            ThresholdState.ABOVE_TARGET,
                            observation.getObservedAt()
                    )
            );
        }

        if (currentState == ThresholdState.REACHED_NOTIFIED) {
            return NotificationDecisionResult.changed(
                    subscription.withThresholdObservation(
                            ThresholdState.REACHED_NOTIFIED,
                            observation.getObservedAt()
                    )
            );
        }

        Subscription updated = subscription.withThresholdObservation(
                ThresholdState.REACHED_NOTIFIED,
                observation.getObservedAt()
        );
        NotificationIntent intent = new NotificationIntent(
                subscription.getId(),
                observation.getSnapshotId(),
                NotificationType.TARGET_REACHED,
                Optional.empty(),
                currentPrice,
                observation.getObservedAt()
        );
        return NotificationDecisionResult.notify(updated, intent);
    }

    private void validateInput(
            Subscription subscription,
            NotificationObservation observation
    ) {
        if (subscription == null || observation == null) {
            throw new IllegalArgumentException("notification decision fields must not be null");
        }
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("notifications can be evaluated only for active subscription");
        }
        if (!subscription.getWatchTargetId().equals(observation.getWatchTargetId())) {
            throw new IllegalArgumentException("observation belongs to another watch target");
        }
        if (observation.getObservedAt().isBefore(subscription.getCreatedAt())) {
            throw new IllegalArgumentException("observation must belong to subscription period");
        }
    }
}
