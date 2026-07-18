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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SubscriptionService {

    public static final int ACTIVE_SUBSCRIPTION_LIMIT = 50;
    private static final Duration QUOTE_TTL = Duration.ofMinutes(15);

    private final UserProfileStore userProfileStore;
    private final SubscriptionStore subscriptionStore;
    private final ImmediateThresholdNotificationPort immediateThresholdNotificationPort;

    public SubscriptionService(
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore,
            ImmediateThresholdNotificationPort immediateThresholdNotificationPort
    ) {
        if (userProfileStore == null || subscriptionStore == null
                || immediateThresholdNotificationPort == null) {
            throw new IllegalArgumentException("subscription service dependencies must not be null");
        }
        this.userProfileStore = userProfileStore;
        this.subscriptionStore = subscriptionStore;
        this.immediateThresholdNotificationPort = immediateThresholdNotificationPort;
    }

    @Transactional
    public SubscriptionPreparationResult prepareFromQuote(
            UUID userId,
            UUID watchTargetId,
            Instant now
    ) {
        validateIdentity(userId, watchTargetId, now);
        return checkEligibility(userId, watchTargetId, now);
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
        SubscriptionPreparationResult preparation = checkEligibility(userId, watchTargetId, now);
        if (!preparation.isReady()) {
            return creationResultFrom(preparation);
        }

        Instant freshNotBefore = now.minus(QUOTE_TTL);
        Optional<SubscriptionQuoteObservation> currentRegularPrice = subscriptionStore
                .findLatestRegularPriceObservation(watchTargetId)
                .filter(observation -> !observation.getObservedAt().isBefore(freshNotBefore))
                .filter(observation -> !observation.getObservedAt().isAfter(now))
                .filter(observation -> observation.getRegularPrice().isPresent());
        Subscription subscription = createSubscription(
                userId,
                watchTargetId,
                mode,
                targetPrice,
                currentRegularPrice,
                now
        );
        Subscription created = subscriptionStore.create(subscription);
        if (created.getNotificationMode() == NotificationMode.TARGET_PRICE
                && created.getThresholdState() == ThresholdState.REACHED_NOTIFIED) {
            immediateThresholdNotificationPort.enqueue(
                    created,
                    currentRegularPrice.orElseThrow(),
                    now
            );
        }
        return SubscriptionCreationResult.created(created);
    }

    @Transactional
    public SubscriptionEndResult end(UUID userId, UUID subscriptionId, Instant now) {
        if (userId == null || subscriptionId == null || now == null) {
            throw new IllegalArgumentException("end subscription fields must not be null");
        }
        if (!userProfileStore.existsAndLockById(userId)) {
            return SubscriptionEndResult.notFound();
        }
        Optional<Subscription> active = subscriptionStore.findActiveOwned(userId, subscriptionId);
        if (active.isEmpty()) {
            return SubscriptionEndResult.notFound();
        }
        Subscription ended = active.get().end(now);
        return SubscriptionEndResult.ended(subscriptionStore.end(ended));
    }

    @Transactional(readOnly = true)
    public List<TrackedSubscriptionItem> findActive(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return subscriptionStore.findActiveByUserId(userId);
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
                    Optional.empty(),
                    SubscriptionStatus.ACTIVE,
                    now,
                    Optional.empty(),
                    0
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
                currentRegularPrice.map(SubscriptionQuoteObservation::getObservedAt),
                SubscriptionStatus.ACTIVE,
                now,
                Optional.empty(),
                0
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

    private void validateIdentity(UUID userId, UUID watchTargetId, Instant now) {
        if (userId == null || watchTargetId == null || now == null) {
            throw new IllegalArgumentException("subscription identity fields must not be null");
        }
    }

    private SubscriptionPreparationResult checkEligibility(
            UUID userId,
            UUID watchTargetId,
            Instant now
    ) {
        if (!userProfileStore.existsAndLockById(userId)) {
            return SubscriptionPreparationResult.failed(
                    SubscriptionPreparationResult.Status.USER_NOT_FOUND
            );
        }
        Optional<Subscription> existing = subscriptionStore.findActive(userId, watchTargetId);
        if (existing.isPresent()) {
            return SubscriptionPreparationResult.alreadyActive(existing.get());
        }
        if (!subscriptionStore.watchTargetExists(watchTargetId)) {
            return SubscriptionPreparationResult.failed(
                    SubscriptionPreparationResult.Status.WATCH_TARGET_NOT_FOUND
            );
        }
        Instant freshNotBefore = now.minus(QUOTE_TTL);
        Optional<SubscriptionQuoteObservation> latestObservation =
                subscriptionStore.findLatestQuoteObservation(watchTargetId);
        if (latestObservation
                .filter(value -> !value.getObservedAt().isBefore(freshNotBefore))
                .filter(value -> !value.getObservedAt().isAfter(now))
                .isEmpty()) {
            return SubscriptionPreparationResult.failed(
                    SubscriptionPreparationResult.Status.QUOTE_EXPIRED
            );
        }
        if (subscriptionStore.countActive(userId) >= ACTIVE_SUBSCRIPTION_LIMIT) {
            return SubscriptionPreparationResult.failed(
                    SubscriptionPreparationResult.Status.LIMIT_REACHED
            );
        }
        return SubscriptionPreparationResult.ready();
    }

    private SubscriptionCreationResult creationResultFrom(
            SubscriptionPreparationResult preparation
    ) {
        return switch (preparation.getStatus()) {
            case ALREADY_ACTIVE -> SubscriptionCreationResult.alreadyActive(
                    preparation.getExistingSubscription().orElseThrow()
            );
            case LIMIT_REACHED -> SubscriptionCreationResult.failed(
                    SubscriptionCreationResult.Status.LIMIT_REACHED
            );
            case QUOTE_EXPIRED -> SubscriptionCreationResult.failed(
                    SubscriptionCreationResult.Status.QUOTE_EXPIRED
            );
            case USER_NOT_FOUND -> SubscriptionCreationResult.failed(
                    SubscriptionCreationResult.Status.USER_NOT_FOUND
            );
            case WATCH_TARGET_NOT_FOUND -> SubscriptionCreationResult.failed(
                    SubscriptionCreationResult.Status.WATCH_TARGET_NOT_FOUND
            );
            case READY -> throw new IllegalArgumentException("READY preparation cannot be a failure");
        };
    }
}
