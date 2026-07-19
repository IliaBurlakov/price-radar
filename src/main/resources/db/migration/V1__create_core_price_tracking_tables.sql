CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    telegram_chat_id BIGINT NOT NULL,
    city_name VARCHAR(100) NOT NULL,
    dest BIGINT NOT NULL,
    spp INTEGER NOT NULL,
    wallet_discount_percent INTEGER NOT NULL DEFAULT 3,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_profiles_city_name_not_blank
        CHECK (btrim(city_name) <> ''),
    CONSTRAINT ck_user_profiles_dest_not_zero
        CHECK (dest <> 0),
    CONSTRAINT ck_user_profiles_spp_non_negative
        CHECK (spp >= 0),
    CONSTRAINT ck_user_profiles_wallet_discount
        CHECK (wallet_discount_percent BETWEEN 0 AND 100)
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    marketplace VARCHAR(32) NOT NULL,
    external_product_id BIGINT NOT NULL,
    canonical_url VARCHAR(1000) NOT NULL,
    title VARCHAR(500),
    brand VARCHAR(255),
    metadata_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_products_marketplace_external_id
        UNIQUE (marketplace, external_product_id),
    CONSTRAINT ck_products_marketplace
        CHECK (marketplace IN ('WILDBERRIES')),
    CONSTRAINT ck_products_external_id_positive
        CHECK (external_product_id > 0),
    CONSTRAINT ck_products_canonical_url_not_blank
        CHECK (btrim(canonical_url) <> '')
);

CREATE TABLE watch_targets (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    variant_kind VARCHAR(32) NOT NULL,
    variant_value VARCHAR(255) NOT NULL,
    variant_display_name VARCHAR(255),
    dest BIGINT NOT NULL,
    spp INTEGER NOT NULL,
    next_check_at TIMESTAMPTZ NOT NULL,
    last_checked_at TIMESTAMPTZ,
    last_successful_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_watch_targets_business_key
        UNIQUE (product_id, variant_kind, variant_value, dest, spp),
    CONSTRAINT ck_watch_targets_variant_kind
        CHECK (variant_kind IN ('SIZE', 'PROVIDER_OPTION', 'NO_VARIANT')),
    CONSTRAINT ck_watch_targets_variant_value
        CHECK (
            (variant_kind = 'SIZE' AND variant_value ~ '^[1-9][0-9]*$')
            OR (variant_kind = 'PROVIDER_OPTION' AND btrim(variant_value) <> '')
            OR (variant_kind = 'NO_VARIANT' AND variant_value = 'NO_VARIANT')
        ),
    CONSTRAINT ck_watch_targets_dest_not_zero
        CHECK (dest <> 0),
    CONSTRAINT ck_watch_targets_spp_non_negative
        CHECK (spp >= 0)
);

CREATE INDEX idx_watch_targets_next_check
    ON watch_targets (next_check_at, id);

CREATE INDEX idx_watch_targets_product
    ON watch_targets (product_id);

CREATE TABLE price_snapshots (
    id UUID PRIMARY KEY,
    check_id UUID NOT NULL UNIQUE,
    watch_target_id UUID NOT NULL REFERENCES watch_targets(id),
    observed_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    price_source VARCHAR(32),
    regular_price_minor BIGINT,
    marketing_base_price_minor BIGINT,
    available BOOLEAN NOT NULL,
    CONSTRAINT ck_price_snapshots_status
        CHECK (status IN ('REGULAR_PRICE', 'BASIC_FALLBACK', 'UNAVAILABLE', 'NO_PRICE')),
    CONSTRAINT ck_price_snapshots_source
        CHECK (price_source IS NULL OR price_source IN ('PRODUCT', 'BASIC_FALLBACK')),
    CONSTRAINT ck_price_snapshots_semantics
        CHECK (
            (
                status = 'REGULAR_PRICE'
                AND price_source = 'PRODUCT'
                AND available = TRUE
                AND regular_price_minor > 0
                AND (marketing_base_price_minor IS NULL OR marketing_base_price_minor > 0)
            )
            OR (
                status = 'BASIC_FALLBACK'
                AND price_source = 'BASIC_FALLBACK'
                AND available = TRUE
                AND regular_price_minor IS NULL
                AND marketing_base_price_minor > 0
            )
            OR (
                status = 'UNAVAILABLE'
                AND price_source IS NULL
                AND regular_price_minor IS NULL
                AND available = FALSE
                AND marketing_base_price_minor IS NULL
            )
            OR (
                status = 'NO_PRICE'
                AND price_source IS NULL
                AND regular_price_minor IS NULL
                AND available = TRUE
                AND marketing_base_price_minor IS NULL
            )
        )
);

CREATE INDEX idx_price_snapshots_history
    ON price_snapshots (watch_target_id, observed_at DESC);

CREATE INDEX idx_price_snapshots_regular_statistics
    ON price_snapshots (watch_target_id, observed_at, regular_price_minor)
    WHERE status = 'REGULAR_PRICE' AND price_source = 'PRODUCT';

CREATE TABLE provider_states (
    marketplace VARCHAR(32) PRIMARY KEY,
    cooldown_until TIMESTAMPTZ,
    last_request_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_provider_states_marketplace
        CHECK (marketplace IN ('WILDBERRIES'))
);

CREATE TABLE telegram_polling_state (
    bot_key VARCHAR(100) PRIMARY KEY,
    last_confirmed_update_id BIGINT,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_telegram_polling_state_bot_key_not_blank
        CHECK (btrim(bot_key) <> '')
);
