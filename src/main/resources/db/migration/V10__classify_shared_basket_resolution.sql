ALTER TABLE pending_shared_basket_imports
    RENAME COLUMN skipped_items TO unresolved_items;

ALTER TABLE pending_shared_basket_imports
    ADD COLUMN available_items INTEGER;

UPDATE pending_shared_basket_imports
SET available_items = found_items - unresolved_items;

ALTER TABLE pending_shared_basket_imports
    ALTER COLUMN available_items SET NOT NULL;

ALTER TABLE pending_shared_basket_imports
    ADD CONSTRAINT ck_pending_shared_basket_available_items
        CHECK (available_items >= 0 AND available_items <= found_items);

CREATE TABLE pending_shared_basket_unavailable_items (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES pending_shared_basket_imports(id) ON DELETE CASCADE,
    item_position INTEGER NOT NULL,
    display_name VARCHAR(760) NOT NULL,
    CONSTRAINT ck_pending_shared_basket_unavailable_position CHECK (item_position >= 0),
    CONSTRAINT ck_pending_shared_basket_unavailable_name CHECK (length(trim(display_name)) > 0),
    CONSTRAINT uk_pending_shared_basket_unavailable_position UNIQUE (import_id, item_position)
);

CREATE INDEX idx_pending_shared_basket_unavailable_import
    ON pending_shared_basket_unavailable_items (import_id, item_position);
