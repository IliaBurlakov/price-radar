package com.priceradar.sharedbasket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_shared_basket_imports")
public class PendingSharedBasketImportEntity {

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "found_items", nullable = false) private int foundItems;
    @Column(name = "skipped_items", nullable = false) private int skippedItems;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    protected PendingSharedBasketImportEntity() {}

    public PendingSharedBasketImportEntity(
            UUID id, UUID userId, int foundItems, int skippedItems, Instant createdAt, Instant expiresAt
    ) {
        this.id = id;
        this.userId = userId;
        this.foundItems = foundItems;
        this.skippedItems = skippedItems;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public int getFoundItems() { return foundItems; }
    public int getSkippedItems() { return skippedItems; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
