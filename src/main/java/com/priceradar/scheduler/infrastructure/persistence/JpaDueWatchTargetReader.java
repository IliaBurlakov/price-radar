package com.priceradar.scheduler.infrastructure.persistence;

import com.priceradar.marketplace.domain.Marketplace;
import com.priceradar.scheduler.application.DueWatchTarget;
import com.priceradar.scheduler.application.DueWatchTargetReader;
import com.priceradar.tracking.domain.VariantKind;
import com.priceradar.tracking.domain.WatchKey;
import com.priceradar.tracking.infrastructure.persistence.DueWatchTargetProjection;
import com.priceradar.tracking.infrastructure.persistence.WatchTargetJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public class JpaDueWatchTargetReader implements DueWatchTargetReader {

    private final WatchTargetJpaRepository repository;

    public JpaDueWatchTargetReader(WatchTargetJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DueWatchTarget> findDue(Instant now, int limit) {
        if (now == null || limit <= 0) {
            throw new IllegalArgumentException("due target query fields are invalid");
        }
        return repository.findDueWithActiveSubscriptions(now, limit).stream()
                .map(this::toDueWatchTarget)
                .toList();
    }

    private DueWatchTarget toDueWatchTarget(DueWatchTargetProjection projection) {
        Marketplace marketplace = Marketplace.valueOf(projection.getMarketplace());
        VariantKind variantKind = VariantKind.valueOf(projection.getVariantKind());
        String variantKey = switch (variantKind) {
            case SIZE -> "SIZE:" + projection.getVariantValue();
            case PROVIDER_OPTION -> "OPTION:" + projection.getVariantValue();
            case NO_VARIANT -> "NO_VARIANT";
        };
        WatchKey watchKey = new WatchKey(
                marketplace,
                projection.getExternalProductId(),
                variantKey,
                projection.getDest(),
                projection.getSpp()
        );
        return new DueWatchTarget(
                projection.getWatchTargetId(),
                projection.getProductId(),
                watchKey,
                projection.getNextCheckAt()
        );
    }
}
