package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.tracking.application.SubscriptionQuoteObservation;
import com.priceradar.tracking.application.SubscriptionStore;
import com.priceradar.tracking.domain.Subscription;
import com.priceradar.tracking.domain.SubscriptionStatus;
import org.springframework.stereotype.Repository;

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
    public Optional<SubscriptionQuoteObservation> findLatestQuoteObservation(UUID watchTargetId) {
        return snapshotRepository.findFirstByWatchTargetIdOrderByObservedAtDesc(watchTargetId)
                .map(snapshot -> new SubscriptionQuoteObservation(
                        snapshot.getObservedAt(),
                        validRegularPrice(snapshot)
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
                subscription.getStatus(),
                subscription.getCreatedAt(),
                subscription.getEndedAt().orElse(null)
        );
        return toSubscription(subscriptionRepository.save(entity));
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
                entity.getStatus(),
                entity.getCreatedAt(),
                Optional.ofNullable(entity.getEndedAt())
        );
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
