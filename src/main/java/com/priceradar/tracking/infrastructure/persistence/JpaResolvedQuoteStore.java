package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.pricing.application.InterpretedPrice;
import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.SnapshotStatus;
import com.priceradar.product.application.PersistedResolvedQuote;
import com.priceradar.product.application.ResolvedQuotePersistenceCommand;
import com.priceradar.product.application.ResolvedQuoteStore;
import com.priceradar.tracking.domain.WatchKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class JpaResolvedQuoteStore implements ResolvedQuoteStore {

    private final WatchTargetJpaRepository watchTargetRepository;
    private final PriceSnapshotJpaRepository snapshotRepository;
    private final Duration firstCheckDelay;
    private final Duration firstCheckMaxJitter;

    public JpaResolvedQuoteStore(
            WatchTargetJpaRepository watchTargetRepository,
            PriceSnapshotJpaRepository snapshotRepository,
            @Value("${priceradar.scheduler.refresh-interval:PT6H}") Duration firstCheckDelay,
            @Value("${priceradar.scheduler.max-jitter:PT30M}") Duration firstCheckMaxJitter
    ) {
        if (firstCheckDelay == null || firstCheckDelay.isZero() || firstCheckDelay.isNegative()) {
            throw new IllegalArgumentException("firstCheckDelay must be positive");
        }
        if (firstCheckMaxJitter == null || firstCheckMaxJitter.isNegative()
                || firstCheckMaxJitter.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("firstCheckMaxJitter must be between zero and 24 hours");
        }
        this.watchTargetRepository = watchTargetRepository;
        this.snapshotRepository = snapshotRepository;
        this.firstCheckDelay = firstCheckDelay;
        this.firstCheckMaxJitter = firstCheckMaxJitter;
    }

    @Override
    @Transactional
    public PersistedResolvedQuote save(UUID productId, ResolvedQuotePersistenceCommand command) {
        WatchKey watchKey = new WatchKey(
                command.getProduct().getMarketplace(),
                command.getNmId(),
                command.getResolvedVariant().getVariantKey(),
                command.getPriceContext().getDest(),
                command.getPriceContext().getSpp()
        );
        UUID watchTargetId = upsertWatchTarget(command, productId, watchKey);
        UUID snapshotId = insertSnapshot(command, watchTargetId);
        return new PersistedResolvedQuote(watchTargetId, snapshotId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findObservationTime(UUID snapshotId) {
        return snapshotRepository.findById(snapshotId)
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
                limitedVariantDisplayName(command).orElse(null),
                command.getPriceContext().getCityName(),
                watchKey.getDest(),
                watchKey.getSpp(),
                command.getObservedAt().plus(firstCheckDelay).plus(initialJitter()),
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

    private Optional<String> limitedVariantDisplayName(ResolvedQuotePersistenceCommand command) {
        return command.getResolvedVariant().getDisplayName().map(value -> {
            int maximumLength = 255;
            if (value.codePointCount(0, value.length()) <= maximumLength) {
                return value;
            }
            return value.substring(0, value.offsetByCodePoints(0, maximumLength));
        });
    }

    private Duration initialJitter() {
        long maximumMillis = firstCheckMaxJitter.toMillis();
        if (maximumMillis == 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(maximumMillis + 1));
    }

    private UUID insertSnapshot(
            ResolvedQuotePersistenceCommand command,
            UUID watchTargetId
    ) {
        InterpretedPrice price = command.getInterpretedPrice();
        SnapshotStatus status = price.getStatus();
        PriceSource source = price.getPriceSource().orElse(null);

        UUID snapshotId = UUID.randomUUID();
        UUID checkId = createCheckId(watchTargetId, command.getObservedAt());
        snapshotRepository.insertIfAbsent(
                snapshotId,
                checkId,
                watchTargetId,
                command.getObservedAt(),
                status.name(),
                source == null ? null : source.name(),
                price.getRegularPrice().map(value -> value.getMinorUnits()).orElse(null),
                price.getMarketingBasePrice().map(value -> value.getMinorUnits()).orElse(null),
                status != SnapshotStatus.UNAVAILABLE
        );
        return snapshotRepository.findByCheckId(checkId)
                .map(PriceSnapshotEntity::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Resolved quote snapshot was not persisted"
                ));
    }

    private UUID createCheckId(UUID watchTargetId, Instant observedAt) {
        String observationIdentity = "resolved-quote:" + watchTargetId + ":" + observedAt;
        return UUID.nameUUIDFromBytes(observationIdentity.getBytes(StandardCharsets.UTF_8));
    }
}
