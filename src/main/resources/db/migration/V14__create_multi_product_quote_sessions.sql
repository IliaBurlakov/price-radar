CREATE TABLE pending_multi_product_quote_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_multi_product_quote_session_ttl CHECK (expires_at > created_at)
);

CREATE INDEX idx_multi_product_quote_sessions_expiry
    ON pending_multi_product_quote_sessions (expires_at);

CREATE TABLE pending_multi_product_quote_items (
    session_id UUID NOT NULL REFERENCES pending_multi_product_quote_sessions(id) ON DELETE CASCADE,
    item_position INTEGER NOT NULL,
    nm_id BIGINT NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    resolution_status VARCHAR(32) NOT NULL,
    quote_payload JSONB,
    PRIMARY KEY (session_id, item_position),
    CONSTRAINT ck_multi_product_quote_item_position CHECK (item_position >= 0),
    CONSTRAINT ck_multi_product_quote_item_nm_id CHECK (nm_id > 0),
    CONSTRAINT ck_multi_product_quote_item_name CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_multi_product_quote_item_status
        CHECK (resolution_status IN ('AVAILABLE', 'UNAVAILABLE', 'UNRESOLVED')),
    CONSTRAINT ck_multi_product_quote_item_payload CHECK (
        (resolution_status = 'UNRESOLVED' AND quote_payload IS NULL)
        OR (resolution_status = 'AVAILABLE' AND quote_payload IS NOT NULL)
        OR (resolution_status = 'UNAVAILABLE')
    )
);
