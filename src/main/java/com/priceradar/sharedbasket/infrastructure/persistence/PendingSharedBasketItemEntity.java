package com.priceradar.sharedbasket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "pending_shared_basket_import_items")
public class PendingSharedBasketItemEntity {

    @Id private UUID id;
    @Column(name = "import_id", nullable = false) private UUID importId;
    @Column(name = "item_position", nullable = false) private int position;
    @Column(name = "watch_target_id", nullable = false) private UUID watchTargetId;
    @Column(name = "snapshot_id", nullable = false) private UUID snapshotId;
    @Column(name = "product_title", length = 500) private String productTitle;

    protected PendingSharedBasketItemEntity() {}

    public PendingSharedBasketItemEntity(
            UUID id, UUID importId, int position, UUID watchTargetId, UUID snapshotId, String productTitle
    ) {
        this.id = id;
        this.importId = importId;
        this.position = position;
        this.watchTargetId = watchTargetId;
        this.snapshotId = snapshotId;
        this.productTitle = productTitle;
    }

    public UUID getImportId() { return importId; }
    public int getPosition() { return position; }
    public UUID getWatchTargetId() { return watchTargetId; }
    public UUID getSnapshotId() { return snapshotId; }
    public String getProductTitle() { return productTitle; }
}
