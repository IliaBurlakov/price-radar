package com.priceradar.product.infrastructure.persistence;

import com.priceradar.marketplace.domain.Marketplace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", nullable = false, length = 32)
    private Marketplace marketplace;

    @Column(name = "external_product_id", nullable = false)
    private long externalProductId;

    @Column(name = "canonical_url", nullable = false, length = 1000)
    private String canonicalUrl;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "brand", length = 255)
    private String brand;

    @Column(name = "metadata_updated_at")
    private Instant metadataUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductEntity() {
    }

    public ProductEntity(
            UUID id,
            Marketplace marketplace,
            long externalProductId,
            String canonicalUrl,
            String title,
            String brand,
            Instant metadataUpdatedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.marketplace = marketplace;
        this.externalProductId = externalProductId;
        this.canonicalUrl = canonicalUrl;
        this.title = title;
        this.brand = brand;
        this.metadataUpdatedAt = metadataUpdatedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public long getExternalProductId() {
        return externalProductId;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getBrand() {
        return brand;
    }

    public Instant getMetadataUpdatedAt() {
        return metadataUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
