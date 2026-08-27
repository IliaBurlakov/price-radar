package com.priceradar.tracking.application;

import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import com.priceradar.tracking.domain.ThresholdState;
import com.priceradar.user.application.UserProfileStore;
import com.priceradar.user.application.UserProfile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SubscriptionService {

    private final UserProfileStore userProfileStore;
    private final SubscriptionStore subscriptionStore;
    private final InitialThresholdNotificationEnqueuer thresholdNotificationEnqueuer;
    private final int activeSubscriptionLimit;
    private final Duration quoteTtl;

    public SubscriptionService(
            UserProfileStore userProfileStore,
            SubscriptionStore subscriptionStore,
            InitialThresholdNotificationEnqueuer thresholdNotificationEnqueuer,
            int activeSubscriptionLimit,
            Duration quoteTtl
    ) {
        if (userProfileStore == null || subscriptionStore == null
                || thresholdNotificationEnqueuer == null || quoteTtl == null) {
            throw new IllegalArgumentException("subscription service dependencies must not be null");
        }
        if (activeSubscriptionLimit <= 0 || quoteTtl.isZero() || quoteTtl.isNegative()) {
            throw new IllegalArgumentException("subscription policy values must be positive");
        }
        this.userProfileStore = userProfileStore;
        this.subscriptionStore = subscriptionStore;
        this.thresholdNotificationEnqueuer = thresholdNotificationEnqueuer;
        this.activeSubscriptionLimit = activeSubscriptionLimit;
        this.quoteTtl = quoteTtl;
    }

    @Transactional
    public SubscriptionPreparationResult prepareFromQuote(
            UUID userId,
            UUID quoteSnapshotId,
            Instant now
    ) {
        validateIdentity(userId, quoteSnapshotId, now);
        return checkEligibility(userId, quoteSnapshotId, now);
    }

    @Transactional
    public SubscriptionCreationResult createFromQuote(
            UUID userId,
            UUID quoteSnapshotId,
            NotificationMode mode,
            Optional<RubleAmount> targetPrice,
            Instant now
    ) {
        validateCreationInput(userId, quoteSnapshotId, mode, targetPrice, now);
        SubscriptionPreparationResult preparation = checkEligibility(userId, quoteSnapshotId, now);
        if (!preparation.isReady()) {
            return creationResultFrom(preparation);
        }

        SubscriptionQuoteObservation quoteObservation = subscriptionStore
                .findQuoteObservation(quoteSnapshotId)
                .orElseThrow(() -> new IllegalStateException("Eligible quote snapshot disappeared"));
        Optional<SubscriptionQuoteObservation> currentRegularPrice = quoteObservation
                .getRegularPrice()
                .isPresent()
                ? Optional.of(quoteObservation)
                : Optional.empty();
        Subscription subscription = createSubscription(
                userId,
                quoteObservation.getWatchTargetId(),
                mode,
                targetPrice,
                currentRegularPrice,
                now
        );
        Subscription persisted = subscriptionStore.create(subscription);
        if (persisted.getThresholdState() == ThresholdState.REACHED_NOTIFIED) {
            thresholdNotificationEnqueuer.enqueue(
                    persisted,
                    currentRegularPrice.orElseThrow(),
                    now
            );
        }
        return SubscriptionCreationResult.created(persisted);
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
    public Optional<Subscription> findActiveOwned(UUID userId, UUID subscriptionId) {
        if (userId == null || subscriptionId == null) {
            throw new IllegalArgumentException("subscription lookup fields must not be null");
        }
        return subscriptionStore.findActiveOwned(userId, subscriptionId);
    }

    @Transactional
    public SubscriptionConditionChangeResult changeToTargetPrice(
            UUID userId,
            UUID subscriptionId,
            RubleAmount targetPrice,
            Instant now
    ) {
        if (userId == null || subscriptionId == null || targetPrice == null || now == null
                || targetPrice.getMinorUnits() == 0) {
            throw new IllegalArgumentException("target condition fields are invalid");
        }
        Optional<Subscription> active = findActiveOwnedForUpdate(userId, subscriptionId);
        if (active.isEmpty()) {
            return SubscriptionConditionChangeResult.of(
                    SubscriptionConditionChangeResult.Status.NOT_FOUND
            );
        }
        Subscription current = active.orElseThrow();
        if (current.getNotificationMode() == NotificationMode.TARGET_PRICE
                && current.getTargetPrice().filter(targetPrice::equals).isPresent()) {
            return SubscriptionConditionChangeResult.unchanged(current);
        }

        SubscriptionPriceHistory history = subscriptionStore.findValidPriceHistory(
                userId,
                subscriptionId,
                now
        );
        Subscription changed = current.changeToTargetPrice(
                targetPrice,
                history.getLatestPrice(),
                history.getLatestObservedAt()
        );
        return persistConditionChange(changed);
    }

    @Transactional
    public SubscriptionConditionChangeResult changeToAnyDecrease(
            UUID userId,
            UUID subscriptionId,
            Instant now
    ) {
        if (userId == null || subscriptionId == null || now == null) {
            throw new IllegalArgumentException("minimum condition fields must not be null");
        }
        Optional<Subscription> active = findActiveOwnedForUpdate(userId, subscriptionId);
        if (active.isEmpty()) {
            return SubscriptionConditionChangeResult.of(
                    SubscriptionConditionChangeResult.Status.NOT_FOUND
            );
        }
        Subscription current = active.orElseThrow();
        if (current.getNotificationMode() == NotificationMode.ANY_DECREASE) {
            return SubscriptionConditionChangeResult.unchanged(current);
        }

        SubscriptionPriceHistory history = subscriptionStore.findValidPriceHistory(
                userId,
                subscriptionId,
                now
        );
        Subscription changed = current.changeToAnyDecrease(
                history.getMinimumPrice(),
                history.getLatestObservedAt()
        );
        return persistConditionChange(changed);
    }

    @Transactional(readOnly = true)
    public List<TrackedSubscriptionItem> findActive(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return subscriptionStore.findActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public ClearSubscriptionsPlan prepareClearAll(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        return clearPlan(subscriptionStore.findActiveSubscriptions(userId));
    }

    @Transactional
    public ClearSubscriptionsResult clearAll(
            UUID userId,
            String confirmedFingerprint,
            Instant now
    ) {
        if (userId == null || confirmedFingerprint == null || now == null) {
            throw new IllegalArgumentException("clear all fields must not be null");
        }
        if (userProfileStore.findByIdAndLock(userId).isEmpty()) {
            return ClearSubscriptionsResult.of(ClearSubscriptionsResult.Status.USER_NOT_FOUND);
        }
        List<Subscription> active = subscriptionStore.findActiveSubscriptions(userId);
        if (active.isEmpty()) {
            return ClearSubscriptionsResult.of(ClearSubscriptionsResult.Status.NOTHING_TO_CLEAR);
        }
        ClearSubscriptionsPlan currentPlan = clearPlan(active);
        if (!MessageDigest.isEqual(
                currentPlan.getFingerprint().getBytes(StandardCharsets.US_ASCII),
                confirmedFingerprint.getBytes(StandardCharsets.US_ASCII)
        )) {
            return ClearSubscriptionsResult.of(ClearSubscriptionsResult.Status.PLAN_CHANGED);
        }
        active.forEach(subscription -> subscriptionStore.end(subscription.end(now)));
        return ClearSubscriptionsResult.cleared(active.size());
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
                currentRegularPrice.map(SubscriptionQuoteObservation::getObservedAt),
                thresholdState,
                currentRegularPrice.map(SubscriptionQuoteObservation::getObservedAt),
                SubscriptionStatus.ACTIVE,
                now,
                Optional.empty(),
                0
        );
    }

    private Optional<Subscription> findActiveOwnedForUpdate(
            UUID userId,
            UUID subscriptionId
    ) {
        if (!userProfileStore.existsAndLockById(userId)) {
            return Optional.empty();
        }
        return subscriptionStore.findActiveOwned(userId, subscriptionId);
    }

    private SubscriptionConditionChangeResult persistConditionChange(Subscription changed) {
        NotificationStateUpdateResult update = subscriptionStore.updateConditionIfActive(changed);
        if (update == NotificationStateUpdateResult.CONFLICT) {
            return SubscriptionConditionChangeResult.of(
                    SubscriptionConditionChangeResult.Status.CONFLICT
            );
        }
        return SubscriptionConditionChangeResult.changed(changed);
    }

    private void validateCreationInput(
            UUID userId,
            UUID quoteSnapshotId,
            NotificationMode mode,
            Optional<RubleAmount> targetPrice,
            Instant now
    ) {
        if (userId == null || quoteSnapshotId == null || mode == null
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

    private void validateIdentity(UUID userId, UUID quoteSnapshotId, Instant now) {
        if (userId == null || quoteSnapshotId == null || now == null) {
            throw new IllegalArgumentException("subscription identity fields must not be null");
        }
    }

    private SubscriptionPreparationResult checkEligibility(
            UUID userId,
            UUID quoteSnapshotId,
            Instant now
    ) {
        UserProfile user = userProfileStore
                .findByIdAndLock(userId)
                .orElse(null);
        if (user == null) {
            return SubscriptionPreparationResult.failed(
                    SubscriptionPreparationResult.Status.USER_NOT_FOUND
            );
        }
        Optional<SubscriptionQuoteObservation> quoteObservation =
                subscriptionStore.findQuoteObservation(quoteSnapshotId);
        if (quoteObservation.isEmpty()) {
            return SubscriptionPreparationResult.failed(
                    SubscriptionPreparationResult.Status.WATCH_TARGET_NOT_FOUND
            );
        }
        if (!sameProviderContext(
                user.getPriceContext(), quoteObservation.orElseThrow().getPriceContext()
        )) {
            return SubscriptionPreparationResult.failed(
                    SubscriptionPreparationResult.Status.REGION_MISMATCH
            );
        }
        UUID watchTargetId = quoteObservation.get().getWatchTargetId();
        Instant freshNotBefore = now.minus(quoteTtl);
        if (quoteObservation
                .filter(value -> !value.getObservedAt().isBefore(freshNotBefore))
                .filter(value -> !value.getObservedAt().isAfter(now))
                .isEmpty()) {
            return SubscriptionPreparationResult.failed(
                    SubscriptionPreparationResult.Status.QUOTE_EXPIRED
            );
        }
        Optional<Subscription> existing = subscriptionStore.findActive(userId, watchTargetId);
        if (existing.isPresent()) {
            return SubscriptionPreparationResult.alreadyActive(existing.get());
        }
        if (subscriptionStore.countActive(userId) >= activeSubscriptionLimit) {
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
            case REGION_MISMATCH -> SubscriptionCreationResult.failed(
                    SubscriptionCreationResult.Status.REGION_MISMATCH
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

    private boolean sameProviderContext(
            PriceContext first,
            PriceContext second
    ) {
        return first.getDest() == second.getDest() && first.getSpp() == second.getSpp();
    }

    private ClearSubscriptionsPlan clearPlan(List<Subscription> subscriptions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            subscriptions.stream().map(Subscription::getId).sorted()
                    .forEach(id -> digest.update(id.toString().getBytes(StandardCharsets.US_ASCII)));
            String fingerprint = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    java.util.Arrays.copyOf(digest.digest(), 8)
            );
            return new ClearSubscriptionsPlan(subscriptions.size(), fingerprint);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
