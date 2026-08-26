CREATE TABLE pending_shared_basket_imports (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    found_items INTEGER NOT NULL,
    skipped_items INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pending_shared_basket_import_counts
        CHECK (found_items >= 0 AND skipped_items >= 0 AND skipped_items <= found_items),
    CONSTRAINT ck_pending_shared_basket_import_lifecycle
        CHECK (expires_at >= created_at)
);

CREATE INDEX idx_pending_shared_basket_import_expiry
    ON pending_shared_basket_imports (expires_at);

CREATE TABLE pending_shared_basket_import_items (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES pending_shared_basket_imports(id) ON DELETE CASCADE,
    item_position INTEGER NOT NULL,
    watch_target_id UUID NOT NULL REFERENCES watch_targets(id) ON DELETE CASCADE,
    snapshot_id UUID NOT NULL REFERENCES price_snapshots(id),
    product_title VARCHAR(500),
    CONSTRAINT ck_pending_shared_basket_item_position CHECK (item_position >= 0),
    CONSTRAINT uk_pending_shared_basket_item_position UNIQUE (import_id, item_position),
    CONSTRAINT uk_pending_shared_basket_item_target UNIQUE (import_id, watch_target_id)
);

CREATE INDEX idx_pending_shared_basket_items_import
    ON pending_shared_basket_import_items (import_id, item_position);
