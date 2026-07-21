package com.priceradar.tracking.infrastructure.persistence;

import com.priceradar.tracking.domain.VariantKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "watch_targets")
public class WatchTargetEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "variant_kind", nullable = false, length = 32)
    private VariantKind variantKind;

    @Column(name = "variant_value", nullable = false, length = 255)
    private String variantValue;

    @Column(name = "variant_display_name", length = 255)
    private String variantDisplayName;

    @Column(name = "city_name", nullable = false, length = 100)
    private String cityName;

    @Column(name = "dest", nullable = false)
    private long dest;

    @Column(name = "spp", nullable = false)
    private int spp;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_successful_at")
    private Instant lastSuccessfulAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WatchTargetEntity() {
    }

    public WatchTargetEntity(
            UUID id,
            UUID productId,
            VariantKind variantKind,
            String variantValue,
            String variantDisplayName,
            String cityName,
            long dest,
            int spp,
            Instant nextCheckAt,
            Instant createdAt
    ) {
        this.id = id;
        this.productId = productId;
        this.variantKind = variantKind;
        this.variantValue = variantValue;
        this.variantDisplayName = variantDisplayName;
        this.cityName = cityName;
        this.dest = dest;
        this.spp = spp;
        this.nextCheckAt = nextCheckAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public VariantKind getVariantKind() {
        return variantKind;
    }

    public String getVariantValue() {
        return variantValue;
    }

    public String getVariantDisplayName() {
        return variantDisplayName;
    }

    public String getCityName() {
        return cityName;
    }

    public long getDest() {
        return dest;
    }

    public int getSpp() {
        return spp;
    }

    public Instant getNextCheckAt() {
        return nextCheckAt;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant getLastSuccessfulAt() {
        return lastSuccessfulAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
