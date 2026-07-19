package com.priceradar.tracking.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import com.priceradar.user.application.UserProfileStore;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class SubscriptionService {

    public static final int ACTIVE_SUBSCRIPTION_LIMIT = 50;
    private static final Duration QUOTE_TTL = Duration.ofMinutes(15);

    private final UserProfileStore userProfileStore;
    private final SubscriptionStore subscriptionStore;

    public SubscriptionService(
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore
    ) {
        if (userProfileStore == null || subscriptionStore == null) {
            throw new IllegalArgumentException("subscription service dependencies must not be null");
        }
        this.userProfileStore = userProfileStore;
        this.subscriptionStore = subscriptionStore;
    }

    @Transactional
    public SubscriptionCreationResult createFromQuote(
            UUID userId,
            UUID watchTargetId,
            NotificationMode mode,
            Optional<RubleAmount> targetPrice,
            Instant now
    ) {
        validateCreationInput(userId, watchTargetId, mode, targetPrice, now);

        if (!userProfileStore.existsAndLockById(userId)) {
            return SubscriptionCreationResult.failed(
                    SubscriptionCreationResult.Status.USER_NOT_FOUND
            );
        }
        Optional<Subscription> existing = subscriptionStore.findActive(userId, watchTargetId);
        if (existing.isPresent()) {
            return SubscriptionCreationResult.alreadyActive(existing.get());
        }
        if (!subscriptionStore.watchTargetExists(watchTargetId)) {
            return SubscriptionCreationResult.failed(
                    SubscriptionCreationResult.Status.WATCH_TARGET_NOT_FOUND
            );
        }
        Instant freshNotBefore = now.minus(QUOTE_TTL);
        Optional<SubscriptionQuoteObservation> latestObservation =
                subscriptionStore.findLatestQuoteObservation(watchTargetId);
        if (latestObservation
                .filter(value -> !value.getObservedAt().isBefore(freshNotBefore))
                .isEmpty()) {
            return SubscriptionCreationResult.failed(
                    SubscriptionCreationResult.Status.QUOTE_EXPIRED
            );
        }
        if (subscriptionStore.countActive(userId) >= ACTIVE_SUBSCRIPTION_LIMIT) {
            return SubscriptionCreationResult.failed(
                    SubscriptionCreationResult.Status.LIMIT_REACHED
            );
        }

        Optional<SubscriptionQuoteObservation> currentRegularPrice = latestObservation
                .filter(observation -> observation.getRegularPrice().isPresent());
        Subscription subscription = createSubscription(
                userId,
                watchTargetId,
                mode,
                targetPrice,
                currentRegularPrice,
                now
        );
        return SubscriptionCreationResult.created(subscriptionStore.create(subscription));
    }

    @Transactional
    public SubscriptionEndResult end(UUID userId, UUID subscriptionId, Instant now) {
        if (userId == null || subscriptionId == null || now == null) {
            throw new IllegalArgumentException("end subscription fields must not be null");
        }
        Optional<Subscription> active = subscriptionStore.findActiveOwned(userId, subscriptionId);
        if (active.isEmpty()) {
            return SubscriptionEndResult.notFound();
        }
        Subscription ended = active.get().end(now);
        return SubscriptionEndResult.ended(subscriptionStore.end(ended));
    }

    private Subscription createSubscription(
            UUID userId,
            UUID watchTargetId,
            NotificationMode mode,
            Optional<RubleAmount> targetPrice,
            Optional<SubscriptionQuoteObservation> currentRegularPrice,
            Instant now
    ) {
        if (mode == NotificationMode.ANY_DECREASE) {
            return new Subscription(
                    UUID.randomUUID(),
                    userId,
                    watchTargetId,
                    mode,
                    Optional.empty(),
                    currentRegularPrice.flatMap(SubscriptionQuoteObservation::getRegularPrice),
                    currentRegularPrice.map(SubscriptionQuoteObservation::getObservedAt),
                    ThresholdState.NOT_APPLICABLE,
                    SubscriptionStatus.ACTIVE,
                    now,
                    Optional.empty()
            );
        }

        RubleAmount target = targetPrice.orElseThrow();
        ThresholdState thresholdState = currentRegularPrice
                .map(observation -> observation.getRegularPrice().orElseThrow().getMinorUnits()
                        <= target.getMinorUnits()
                        ? ThresholdState.REACHED_NOTIFIED
                        : ThresholdState.ABOVE_TARGET)
                .orElse(ThresholdState.UNKNOWN);
        return new Subscription(
                UUID.randomUUID(),
                userId,
                watchTargetId,
                mode,
                Optional.of(target),
                Optional.empty(),
                Optional.empty(),
                thresholdState,
                SubscriptionStatus.ACTIVE,
                now,
                Optional.empty()
        );
    }

    private void validateCreationInput(
            UUID userId,
            UUID watchTargetId,
            NotificationMode mode,
            Optional<RubleAmount> targetPrice,
            Instant now
    ) {
        if (userId == null || watchTargetId == null || mode == null
                || targetPrice == null || now == null) {
            throw new IllegalArgumentException("subscription creation fields must not be null");
        }
        if (mode == NotificationMode.ANY_DECREASE && targetPrice.isPresent()) {
            throw new IllegalArgumentException("ANY_DECREASE must not have targetPrice");
        }
        if (mode == NotificationMode.TARGET_PRICE
                && targetPrice.filter(price -> price.getMinorUnits() > 0).isEmpty()) {
            throw new IllegalArgumentException("TARGET_PRICE requires positive targetPrice");
        }
    }
}
