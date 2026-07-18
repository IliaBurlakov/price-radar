ALTER TABLE watch_targets
    ADD COLUMN city_name VARCHAR(100) NOT NULL DEFAULT 'Moscow';

ALTER TABLE watch_targets
    ALTER COLUMN city_name DROP DEFAULT;

ALTER TABLE watch_targets
    ADD CONSTRAINT ck_watch_targets_city_name_not_blank
        CHECK (btrim(city_name) <> '');

ALTER TABLE subscriptions
    ADD COLUMN threshold_observed_at TIMESTAMPTZ;

UPDATE subscriptions subscription
SET threshold_observed_at = COALESCE(
        (
            SELECT MAX(snapshot.observed_at)
            FROM price_snapshots snapshot
            WHERE snapshot.watch_target_id = subscription.watch_target_id
              AND snapshot.status = 'REGULAR_PRICE'
              AND snapshot.price_source = 'PRODUCT'
        ),
        subscription.created_at
    )
WHERE subscription.notification_mode = 'TARGET_PRICE'
  AND subscription.threshold_state IN ('ABOVE_TARGET', 'REACHED_NOTIFIED');

ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_threshold_observation
        CHECK (
            (
                notification_mode = 'ANY_DECREASE'
                AND threshold_observed_at IS NULL
            )
            OR (
                notification_mode = 'TARGET_PRICE'
                AND threshold_state = 'UNKNOWN'
                AND threshold_observed_at IS NULL
            )
            OR (
                notification_mode = 'TARGET_PRICE'
                AND threshold_state IN ('ABOVE_TARGET', 'REACHED_NOTIFIED')
                AND threshold_observed_at IS NOT NULL
            )
        );

ALTER TABLE telegram_polling_state
    ADD COLUMN failed_update_id BIGINT,
    ADD COLUMN failed_update_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE telegram_polling_state
    ADD CONSTRAINT ck_telegram_polling_state_failure
        CHECK (
            (failed_update_id IS NULL AND failed_update_attempts = 0)
            OR (failed_update_id IS NOT NULL AND failed_update_attempts > 0)
        );

CREATE TABLE telegram_pending_target_prices (
    telegram_user_id BIGINT PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    watch_target_id UUID NOT NULL REFERENCES watch_targets(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_telegram_pending_target_prices_identifiers
        CHECK (telegram_user_id > 0 AND chat_id > 0),
    CONSTRAINT ck_telegram_pending_target_prices_lifecycle
        CHECK (expires_at >= created_at)
);

CREATE INDEX idx_telegram_pending_target_prices_expiry
    ON telegram_pending_target_prices (expires_at);
