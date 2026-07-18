package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.PriceContext;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.LatestSnapshotView;
import com.priceradar.tracking.application.NotificationStateUpdateResult;
import com.priceradar.tracking.application.SubscriptionQuoteObservation;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.application.TrackedSubscriptionItem;
import com.priceradar.tracking.domain.NotificationMode;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaSubscriptionStore implements SubscriptionStore {

    private final SubscriptionJpaRepository subscriptionRepository;
    private final WatchTargetJpaRepository watchTargetRepository;
    private final PriceSnapshotJpaRepository snapshotRepository;

    public JpaSubscriptionStore(
            SubscriptionJpaRepository subscriptionRepository,
            WatchTargetJpaRepository watchTargetRepository,
            PriceSnapshotJpaRepository snapshotRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.watchTargetRepository = watchTargetRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    public boolean watchTargetExists(UUID watchTargetId) {
        return watchTargetRepository.existsById(watchTargetId);
    }

    @Override
    public Optional<Subscription> findActive(UUID userId, UUID watchTargetId) {
        return subscriptionRepository.findByUserIdAndWatchTargetIdAndStatus(
                userId,
                watchTargetId,
                SubscriptionStatus.ACTIVE
        ).map(this::toSubscription);
    }

    @Override
    public Optional<Subscription> findActiveOwned(UUID userId, UUID subscriptionId) {
        return subscriptionRepository.findByIdAndUserIdAndStatus(
                subscriptionId,
                userId,
                SubscriptionStatus.ACTIVE
        ).map(this::toSubscription);
    }

    @Override
    public long countActive(UUID userId) {
        return subscriptionRepository.countByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
    }

    @Override
    public List<TrackedSubscriptionItem> findActiveByUserId(UUID userId) {
        return subscriptionRepository.findActiveTrackedItems(userId).stream()
                .map(this::toTrackedSubscriptionItem)
                .toList();
    }

    @Override
    public Optional<LatestSnapshotView> findLatestSnapshotActiveOwned(
            UUID userId,
            UUID subscriptionId
    ) {
        return subscriptionRepository
                .findLatestSnapshotActiveOwned(userId, subscriptionId)
                .map(this::toLatestSnapshotView);
    }

    @Override
    public Optional<SubscriptionQuoteObservation> findLatestQuoteObservation(UUID watchTargetId) {
        return snapshotRepository.findFirstByWatchTargetIdOrderByObservedAtDesc(watchTargetId)
                .map(snapshot -> new SubscriptionQuoteObservation(
                        snapshot.getId(),
                        snapshot.getObservedAt(),
                        validRegularPrice(snapshot)
                ));
    }

    @Override
    public Optional<SubscriptionQuoteObservation> findLatestRegularPriceObservation(
            UUID watchTargetId
    ) {
        return snapshotRepository
                .findFirstByWatchTargetIdAndStatusAndPriceSourceOrderByObservedAtDesc(
                        watchTargetId,
                        SnapshotStatus.REGULAR_PRICE,
                        PriceSource.PRODUCT
                )
                .map(snapshot -> new SubscriptionQuoteObservation(
                        snapshot.getId(),
                        snapshot.getObservedAt(),
                        optionalAmount(snapshot.getRegularPriceMinor())
                ));
    }

    @Override
    public Subscription create(Subscription subscription) {
        SubscriptionEntity entity = new SubscriptionEntity(
                subscription.getId(),
                subscription.getUserId(),
                subscription.getWatchTargetId(),
                subscription.getNotificationMode(),
                subscription.getTargetPrice().map(RubleAmount::getMinorUnits).orElse(null),
                subscription.getBaselinePrice().map(RubleAmount::getMinorUnits).orElse(null),
                subscription.getBaselineObservedAt().orElse(null),
                subscription.getThresholdState(),
                subscription.getThresholdObservedAt().orElse(null),
                subscription.getStatus(),
                subscription.getCreatedAt(),
                subscription.getEndedAt().orElse(null)
        );
        return toSubscription(subscriptionRepository.save(entity));
    }

    @Override
    public NotificationStateUpdateResult updateNotificationStateIfActive(Subscription subscription) {
        if (subscription == null) {
            throw new IllegalArgumentException("subscription must not be null");
        }
        int updated = subscriptionRepository.updateNotificationStateIfActive(
                subscription.getId(),
                subscription.getBaselinePrice().map(RubleAmount::getMinorUnits).orElse(null),
                subscription.getBaselineObservedAt().orElse(null),
                subscription.getThresholdState(),
                subscription.getThresholdObservedAt().orElse(null),
                SubscriptionStatus.ACTIVE,
                subscription.getVersion()
        );
        return updated == 1
                ? NotificationStateUpdateResult.UPDATED
                : NotificationStateUpdateResult.CONFLICT;
    }

    @Override
    public Subscription end(Subscription subscription) {
        SubscriptionEntity entity = subscriptionRepository.findById(subscription.getId())
                .orElseThrow(() -> new IllegalStateException("Subscription no longer exists"));
        entity.end(subscription.getEndedAt().orElseThrow());
        return toSubscription(entity);
    }

    private Subscription toSubscription(SubscriptionEntity entity) {
        return new Subscription(
                entity.getId(),
                entity.getUserId(),
                entity.getWatchTargetId(),
                entity.getNotificationMode(),
                optionalAmount(entity.getTargetPriceMinor()),
                optionalAmount(entity.getBaselinePriceMinor()),
                Optional.ofNullable(entity.getBaselineObservedAt()),
                entity.getThresholdState(),
                Optional.ofNullable(entity.getThresholdObservedAt()),
                entity.getStatus(),
                entity.getCreatedAt(),
                Optional.ofNullable(entity.getEndedAt()),
                entity.getVersion()
        );
    }

    private TrackedSubscriptionItem toTrackedSubscriptionItem(
            TrackedSubscriptionProjection projection
    ) {
        Optional<SnapshotStatus> snapshotStatus = Optional
                .ofNullable(projection.getSnapshotStatus())
                .map(SnapshotStatus::valueOf);
        Optional<RubleAmount> regularPrice = validRegularPrice(
                projection,
                snapshotStatus
        );
        return new TrackedSubscriptionItem(
                projection.getSubscriptionId(),
                projection.getNmId(),
                Optional.ofNullable(projection.getTitle()),
                Optional.ofNullable(projection.getBrand()),
                projection.getCanonicalUrl(),
                Optional.ofNullable(projection.getVariantDisplayName()),
                NotificationMode.valueOf(projection.getNotificationMode()),
                optionalAmount(projection.getTargetPriceMinor()),
                projection.getTrackingStartedAt(),
                snapshotStatus,
                regularPrice,
                Optional.ofNullable(projection.getObservedAt())
        );
    }

    private LatestSnapshotView toLatestSnapshotView(
            LatestSnapshotProjection projection
    ) {
        Optional<InterpretedPrice> interpretedPrice = Optional
                .ofNullable(projection.getSnapshotStatus())
                .map(status -> new InterpretedPrice(
                        optionalAmount(projection.getRegularPriceMinor()),
                        optionalAmount(projection.getMarketingBasePriceMinor()),
                        Optional.ofNullable(projection.getPriceSource())
                                .map(PriceSource::valueOf),
                        SnapshotStatus.valueOf(status)
                ));
        return new LatestSnapshotView(
                projection.getSubscriptionId(),
                projection.getNmId(),
                Optional.ofNullable(projection.getTitle()),
                Optional.ofNullable(projection.getBrand()),
                projection.getCanonicalUrl(),
                Optional.ofNullable(projection.getVariantDisplayName()),
                new PriceContext(
                        projection.getCityName(),
                        projection.getDest(),
                        projection.getSpp()
                ),
                interpretedPrice,
                Optional.ofNullable(projection.getObservedAt())
        );
    }

    private Optional<RubleAmount> validRegularPrice(
            TrackedSubscriptionProjection projection,
            Optional<SnapshotStatus> status
    ) {
        if (status.filter(SnapshotStatus.REGULAR_PRICE::equals).isEmpty()
                || !PriceSource.PRODUCT.name().equals(projection.getPriceSource())) {
            return Optional.empty();
        }
        return optionalAmount(projection.getRegularPriceMinor());
    }

    private Optional<RubleAmount> optionalAmount(Long minorUnits) {
        return Optional.ofNullable(minorUnits).map(RubleAmount::ofMinorUnits);
    }

    private Optional<RubleAmount> validRegularPrice(PriceSnapshotEntity snapshot) {
        if (snapshot.getStatus() != SnapshotStatus.REGULAR_PRICE
                || snapshot.getPriceSource() != PriceSource.PRODUCT) {
            return Optional.empty();
        }
        return optionalAmount(snapshot.getRegularPriceMinor());
    }
}
