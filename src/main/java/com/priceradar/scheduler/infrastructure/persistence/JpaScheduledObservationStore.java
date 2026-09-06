package com.priceradar.scheduler.infrastructure.persistence;

import com.priceradar.marketplace.application.MarketplaceProductDetails;
import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.RubleAmount;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.infrastructure.persistence.ProductJpaRepository;
import com.priceradar.scheduler.application.ScheduledObservationStore;
import com.priceradar.tracking.infrastructure.persistence.PriceSnapshotEntity;
import com.priceradar.tracking.infrastructure.persistence.PriceSnapshotJpaRepository;
import com.priceradar.tracking.infrastructure.persistence.WatchTargetJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JpaScheduledObservationStore implements ScheduledObservationStore {

    private final PriceSnapshotJpaRepository snapshotRepository;
    private final ProductJpaRepository productRepository;
    private final WatchTargetJpaRepository watchTargetRepository;

    public JpaScheduledObservationStore(
            PriceSnapshotJpaRepository snapshotRepository,
            ProductJpaRepository productRepository,
            WatchTargetJpaRepository watchTargetRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.productRepository = productRepository;
        this.watchTargetRepository = watchTargetRepository;
    }

    @Override
    public UUID saveSnapshot(
            UUID checkId,
            UUID watchTargetId,
            InterpretedPrice price,
            Instant observedAt
    ) {
        if (checkId == null || watchTargetId == null || price == null || observedAt == null) {
            throw new IllegalArgumentException("scheduled snapshot fields must not be null");
        }
        UUID snapshotId = UUID.randomUUID();
        int inserted = snapshotRepository.insertIfAbsent(
                snapshotId,
                checkId,
                watchTargetId,
                observedAt,
                price.getStatus().name(),
                price.getPriceSource().map(PriceSource::name).orElse(null),
                price.getRegularPrice().map(RubleAmount::getMinorUnits).orElse(null),
                price.getMarketingBasePrice().map(RubleAmount::getMinorUnits).orElse(null),
                price.getStatus() != SnapshotStatus.UNAVAILABLE
        );
        if (inserted == 1) {
            return snapshotId;
        }
        PriceSnapshotEntity existing = snapshotRepository.findByCheckId(checkId)
                .orElseThrow(() -> new IllegalStateException("Snapshot insert conflict was not found"));
        if (!existing.getWatchTargetId().equals(watchTargetId)) {
            throw new IllegalStateException("checkId belongs to another watch target");
        }
        validateExistingSnapshot(existing, price, observedAt);
        return existing.getId();
    }

    @Override
    public void updateProductMetadata(
            UUID productId,
            MarketplaceProductDetails product,
            Instant observedAt
    ) {
        if (productId == null || product == null || observedAt == null) {
            throw new IllegalArgumentException("product metadata fields must not be null");
        }
        productRepository.updateMetadataIfNewer(
                productId,
                null,
                product.getTitle().orElse(null),
                product.getBrand().orElse(null),
                observedAt
        );
    }

    @Override
    public void markSuccessful(
            UUID watchTargetId,
            Instant completedAt,
            Instant nextCheckAt
    ) {
        validateSchedule(watchTargetId, completedAt, nextCheckAt);
        if (watchTargetRepository.markSuccessfulCheck(
                watchTargetId,
                completedAt,
                nextCheckAt
        ) != 1) {
            throw new IllegalStateException("Watch target no longer exists");
        }
    }

    @Override
    public void markFailed(
            UUID watchTargetId,
            Instant completedAt,
            Instant nextCheckAt
    ) {
        validateSchedule(watchTargetId, completedAt, nextCheckAt);
        if (watchTargetRepository.markFailedCheck(
                watchTargetId,
                completedAt,
                nextCheckAt
        ) != 1) {
            throw new IllegalStateException("Watch target no longer exists");
        }
    }

    private void validateSchedule(
            UUID watchTargetId,
            Instant completedAt,
            Instant nextCheckAt
    ) {
        if (watchTargetId == null || completedAt == null || nextCheckAt == null) {
            throw new IllegalArgumentException("watch target schedule fields must not be null");
        }
        if (!nextCheckAt.isAfter(completedAt)) {
            throw new IllegalArgumentException("nextCheckAt must be after completedAt");
        }
    }

    private void validateExistingSnapshot(
            PriceSnapshotEntity existing,
            InterpretedPrice price,
            Instant observedAt
    ) {
        Long regularPriceMinor = price.getRegularPrice()
                .map(RubleAmount::getMinorUnits)
                .orElse(null);
        Long marketingBasePriceMinor = price.getMarketingBasePrice()
                .map(RubleAmount::getMinorUnits)
                .orElse(null);
        PriceSource priceSource = price.getPriceSource().orElse(null);
        boolean available = price.getStatus() != SnapshotStatus.UNAVAILABLE;
        if (!existing.getObservedAt().truncatedTo(ChronoUnit.MICROS)
                .equals(observedAt.truncatedTo(ChronoUnit.MICROS))
                || existing.getStatus() != price.getStatus()
                || existing.getPriceSource() != priceSource
                || !Objects.equals(existing.getRegularPriceMinor(), regularPriceMinor)
                || !Objects.equals(existing.getMarketingBasePriceMinor(), marketingBasePriceMinor)
                || existing.isAvailable() != available) {
            throw new IllegalStateException("checkId was reused with another observation");
        }
    }
}
