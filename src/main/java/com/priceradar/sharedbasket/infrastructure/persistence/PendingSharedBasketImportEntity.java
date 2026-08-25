package com.priceradar.sharedbasket.infrastructure.persistence;

import com.priceradar.region.domain.MarketplaceRegionCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_shared_basket_imports")
public class PendingSharedBasketImportEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", nullable = false, length = 32)
    private MarketplaceRegionCode regionCode;
    @Column(name = "found_items", nullable = false)
    private int foundItems;

    @Column(name = "available_items", nullable = false)
    private int availableItems;

    @Column(name = "unresolved_items", nullable = false)
    private int unresolvedItems;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PendingSharedBasketImportEntity() {
    }

    public PendingSharedBasketImportEntity(
            UUID id, UUID userId, MarketplaceRegionCode regionCode,
            int foundItems, int availableItems, int unresolvedItems,
            Instant createdAt, Instant expiresAt
    ) {
        this.id = id;
        this.userId = userId;
        this.regionCode = regionCode;
        this.foundItems = foundItems;
        this.availableItems = availableItems;
        this.unresolvedItems = unresolvedItems;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public MarketplaceRegionCode getRegionCode() {
        return regionCode;
    }

    public int getFoundItems() {
        return foundItems;
    }

    public int getAvailableItems() {
        return availableItems;
    }

    public int getUnresolvedItems() {
        return unresolvedItems;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
