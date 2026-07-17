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
        Optional<RubleAmount> previousPrice = subscription.getBaselinePrice();
        Subscription updated = subscription.withBaseline(
                currentPrice,
                observation.getObservedAt()
        );
        if (previousPrice.isEmpty()
                || currentPrice.getMinorUnits() >= previousPrice.orElseThrow().getMinorUnits()) {
            return NotificationDecisionResult.changed(updated);
        }

        NotificationIntent intent = new NotificationIntent(
                subscription.getId(),
                observation.getSnapshotId(),
                NotificationType.PRICE_DECREASE,
                previousPrice,
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
        RubleAmount targetPrice = subscription.getTargetPrice().orElseThrow();
        boolean targetReached = currentPrice.getMinorUnits() <= targetPrice.getMinorUnits();
        ThresholdState currentState = subscription.getThresholdState();

        if (!targetReached) {
            if (currentState == ThresholdState.ABOVE_TARGET) {
                return NotificationDecisionResult.unchanged(subscription);
            }
            return NotificationDecisionResult.changed(
                    subscription.withThresholdState(ThresholdState.ABOVE_TARGET)
            );
        }

        if (currentState == ThresholdState.REACHED_NOTIFIED) {
            return NotificationDecisionResult.unchanged(subscription);
        }

        Subscription updated = subscription.withThresholdState(
                ThresholdState.REACHED_NOTIFIED
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
