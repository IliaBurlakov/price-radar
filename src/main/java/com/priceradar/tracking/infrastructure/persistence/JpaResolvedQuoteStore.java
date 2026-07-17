package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.application.ResolvedQuotePersistenceCommand;
import com.priceradar.product.application.ResolvedQuoteStore;
import com.priceradar.tracking.domain.WatchKey;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaResolvedQuoteStore implements ResolvedQuoteStore {

    private static final Duration FIRST_CHECK_DELAY = Duration.ofHours(6);

    private final WatchTargetJpaRepository watchTargetRepository;
    private final PriceSnapshotJpaRepository snapshotRepository;

    public JpaResolvedQuoteStore(
            WatchTargetJpaRepository watchTargetRepository,
            PriceSnapshotJpaRepository snapshotRepository
    ) {
        this.watchTargetRepository = watchTargetRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    @Transactional
    public UUID save(UUID productId, ResolvedQuotePersistenceCommand command) {
        WatchKey watchKey = new WatchKey(
                command.getProduct().getMarketplace(),
                command.getNmId(),
                command.getResolvedVariant().getVariantKey(),
                command.getPriceContext().getDest(),
                command.getPriceContext().getSpp()
        );
        UUID watchTargetId = upsertWatchTarget(command, productId, watchKey);
        insertSnapshot(command, watchTargetId);
        return watchTargetId;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findLatestObservationTime(UUID watchTargetId) {
        return snapshotRepository.findFirstByWatchTargetIdOrderByObservedAtDesc(watchTargetId)
                .map(PriceSnapshotEntity::getObservedAt);
    }

    private UUID upsertWatchTarget(
            ResolvedQuotePersistenceCommand command,
            UUID productId,
            WatchKey watchKey
    ) {
        watchTargetRepository.insertIfAbsent(
                UUID.randomUUID(),
                productId,
                watchKey.getVariantKind().name(),
                watchKey.getVariantValue(),
                command.getResolvedVariant().getDisplayName().orElse(null),
                watchKey.getDest(),
                watchKey.getSpp(),
                command.getObservedAt().plus(FIRST_CHECK_DELAY),
                command.getObservedAt()
        );

        WatchTargetEntity watchTarget = watchTargetRepository
                .findByProductIdAndVariantKindAndVariantValueAndDestAndSpp(
                        productId,
                        watchKey.getVariantKind(),
                        watchKey.getVariantValue(),
                        watchKey.getDest(),
                        watchKey.getSpp()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Watch target upsert did not return a target"
                ));
        return watchTarget.getId();
    }

    private void insertSnapshot(
            ResolvedQuotePersistenceCommand command,
            UUID watchTargetId
    ) {
        InterpretedPrice price = command.getInterpretedPrice();
        SnapshotStatus status = price.getStatus();
        PriceSource source = price.getPriceSource().orElse(null);

        snapshotRepository.insertIfAbsent(
                UUID.randomUUID(),
                createCheckId(watchTargetId, command.getObservedAt()),
                watchTargetId,
                command.getObservedAt(),
                status.name(),
                source == null ? null : source.name(),
                price.getRegularPrice().map(value -> value.getMinorUnits()).orElse(null),
                price.getMarketingBasePrice().map(value -> value.getMinorUnits()).orElse(null),
                status != SnapshotStatus.UNAVAILABLE
        );
    }

    private UUID createCheckId(UUID watchTargetId, Instant observedAt) {
        String observationIdentity = "resolved-quote:" + watchTargetId + ":" + observedAt;
        return UUID.nameUUIDFromBytes(observationIdentity.getBytes(StandardCharsets.UTF_8));
    }
}
