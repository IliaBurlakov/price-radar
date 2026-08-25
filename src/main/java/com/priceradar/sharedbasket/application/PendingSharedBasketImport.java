package com.priceradar.sharedbasket.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.priceradar.region.domain.MarketplaceRegionCode;

public final class PendingSharedBasketImport {

    private final UUID id;
    private final UUID userId;
    private final MarketplaceRegionCode regionCode;
    private final int foundItems;
    private final int availableItems;
    private final int unresolvedItems;
    private final List<PendingUnavailableSharedBasketItem> unavailableItems;
    private final List<PendingSharedBasketItem> items;
    private final Instant createdAt;
    private final Instant expiresAt;

    public PendingSharedBasketImport(
            UUID id,
            UUID userId,
            MarketplaceRegionCode regionCode,
            int foundItems,
            int availableItems,
            int unresolvedItems,
            List<PendingUnavailableSharedBasketItem> unavailableItems,
            List<PendingSharedBasketItem> items,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (id == null || userId == null || regionCode == null
                || unavailableItems == null || items == null
                || createdAt == null || expiresAt == null
                || foundItems < 0 || availableItems < 0 || unresolvedItems < 0
                || availableItems < items.size()
                || foundItems != availableItems + unavailableItems.size() + unresolvedItems
                || unavailableItems.stream().anyMatch(item -> item == null)
                || items.stream().anyMatch(item -> item == null)
                || expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("pending shared basket import fields are invalid");
        }
        this.id = id;
        this.userId = userId;
        this.regionCode = regionCode;
        this.foundItems = foundItems;
        this.availableItems = availableItems;
        this.unresolvedItems = unresolvedItems;
        this.unavailableItems = List.copyOf(unavailableItems);
        this.items = List.copyOf(items);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public MarketplaceRegionCode getRegionCode() { return regionCode; }
    public int getFoundItems() { return foundItems; }
    public int getAvailableItems() { return availableItems; }
    public int getUnresolvedItems() { return unresolvedItems; }
    public List<PendingUnavailableSharedBasketItem> getUnavailableItems() { return unavailableItems; }
    public List<PendingSharedBasketItem> getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
}
