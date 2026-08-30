package com.priceradar.telegram.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class MultiProductQuoteSession {

    private final UUID id;
    private final UUID userId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final List<MultiProductQuoteItem> items;

    public MultiProductQuoteSession(
            UUID id,
            UUID userId,
            Instant createdAt,
            Instant expiresAt,
            List<MultiProductQuoteItem> items
    ) {
        if (id == null || userId == null || createdAt == null || expiresAt == null
                || items == null || items.isEmpty() || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("multi-product quote session is invalid");
        }
        List<MultiProductQuoteItem> ordered = items.stream()
                .sorted(Comparator.comparingInt(MultiProductQuoteItem::getPosition))
                .toList();
        for (int position = 0; position < ordered.size(); position++) {
            if (ordered.get(position).getPosition() != position) {
                throw new IllegalArgumentException("multi-product positions must be contiguous");
            }
        }
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.items = List.copyOf(ordered);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public List<MultiProductQuoteItem> getItems() { return items; }
    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
}
