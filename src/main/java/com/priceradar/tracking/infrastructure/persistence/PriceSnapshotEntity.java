package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.pricing.domain.PriceSource;
import com.priceradar.pricing.domain.SnapshotStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_snapshots")
public class PriceSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "check_id", nullable = false, unique = true)
    private UUID checkId;

    @Column(name = "watch_target_id", nullable = false)
    private UUID watchTargetId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SnapshotStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_source", length = 32)
    private PriceSource priceSource;

    @Column(name = "regular_price_minor")
    private Long regularPriceMinor;

    @Column(name = "marketing_base_price_minor")
    private Long marketingBasePriceMinor;

    @Column(name = "available", nullable = false)
    private boolean available;

    protected PriceSnapshotEntity() {
    }

    public PriceSnapshotEntity(
            UUID id,
            UUID checkId,
            UUID watchTargetId,
            Instant observedAt,
            SnapshotStatus status,
            PriceSource priceSource,
            Long regularPriceMinor,
            Long marketingBasePriceMinor,
            boolean available
    ) {
        this.id = id;
        this.checkId = checkId;
        this.watchTargetId = watchTargetId;
        this.observedAt = observedAt;
        this.status = status;
        this.priceSource = priceSource;
        this.regularPriceMinor = regularPriceMinor;
        this.marketingBasePriceMinor = marketingBasePriceMinor;
        this.available = available;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public UUID getWatchTargetId() {
        return watchTargetId;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public SnapshotStatus getStatus() {
        return status;
    }

    public PriceSource getPriceSource() {
        return priceSource;
    }

    public Long getRegularPriceMinor() {
        return regularPriceMinor;
    }

    public Long getMarketingBasePriceMinor() {
        return marketingBasePriceMinor;
    }

    public boolean isAvailable() {
        return available;
    }
}
