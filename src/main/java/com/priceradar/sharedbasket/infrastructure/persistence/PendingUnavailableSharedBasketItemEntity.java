package com.priceradar.sharedbasket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "pending_shared_basket_unavailable_items")
public class PendingUnavailableSharedBasketItemEntity {

    @Id private UUID id;
    @Column(name = "import_id", nullable = false) private UUID importId;
    @Column(name = "item_position", nullable = false) private int position;
    @Column(name = "display_name", nullable = false, length = 760) private String displayName;

    protected PendingUnavailableSharedBasketItemEntity() {}

    public PendingUnavailableSharedBasketItemEntity(
            UUID id, UUID importId, int position, String displayName
    ) {
        this.id = id;
        this.importId = importId;
        this.position = position;
        this.displayName = displayName;
    }

    public int getPosition() { return position; }
    public String getDisplayName() { return displayName; }
}
