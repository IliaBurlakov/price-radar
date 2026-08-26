package com.priceradar.sharedbasket.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PendingSharedBasketImport {

    private final UUID id;
    private final UUID userId;
    private final int foundItems;
    private final int skippedItems;
    private final List<PendingSharedBasketItem> items;
    private final Instant createdAt;
    private final Instant expiresAt;

    public PendingSharedBasketImport(
            UUID id,
            UUID userId,
            int foundItems,
            int skippedItems,
            List<PendingSharedBasketItem> items,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (id == null || userId == null || items == null || createdAt == null || expiresAt == null
                || foundItems < 0 || skippedItems < 0 || foundItems != items.size() + skippedItems
                || items.stream().anyMatch(item -> item == null)
                || expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("pending shared basket import fields are invalid");
        }
        this.id = id;
        this.userId = userId;
        this.foundItems = foundItems;
        this.skippedItems = skippedItems;
        this.items = List.copyOf(items);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public int getFoundItems() { return foundItems; }
    public int getSkippedItems() { return skippedItems; }
    public List<PendingSharedBasketItem> getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
}
