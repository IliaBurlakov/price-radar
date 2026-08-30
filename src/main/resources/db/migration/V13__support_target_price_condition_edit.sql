ALTER TABLE telegram_pending_target_prices
    ADD COLUMN purpose VARCHAR(32) NOT NULL DEFAULT 'CREATE_SUBSCRIPTION',
    ADD COLUMN subscription_id UUID REFERENCES subscriptions(id) ON DELETE CASCADE;

ALTER TABLE telegram_pending_target_prices
    ALTER COLUMN purpose DROP DEFAULT,
    ALTER COLUMN quote_snapshot_id DROP NOT NULL,
    ADD CONSTRAINT ck_telegram_pending_target_prices_purpose
        CHECK (purpose IN ('CREATE_SUBSCRIPTION', 'EDIT_SUBSCRIPTION')),
    ADD CONSTRAINT ck_telegram_pending_target_prices_reference
        CHECK (
            (purpose = 'CREATE_SUBSCRIPTION' AND quote_snapshot_id IS NOT NULL AND subscription_id IS NULL)
            OR
            (purpose = 'EDIT_SUBSCRIPTION' AND quote_snapshot_id IS NULL AND subscription_id IS NOT NULL)
        );

ALTER TABLE subscriptions
    DROP CONSTRAINT ck_subscriptions_notification_reference_pair,
    DROP CONSTRAINT ck_subscriptions_notification_state;

ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_notification_reference_pair
        CHECK (
            notification_reference_price_minor IS NULL
            OR (
                notification_reference_price_minor > 0
                AND last_processed_price_observed_at IS NOT NULL
            )
        ),
    ADD CONSTRAINT ck_subscriptions_notification_state
        CHECK (
            (
                notification_mode = 'ANY_DECREASE'
                AND target_price_minor IS NULL
                AND (
                    (notification_reference_price_minor IS NULL AND last_processed_price_observed_at IS NULL)
                    OR
                    (notification_reference_price_minor > 0 AND last_processed_price_observed_at IS NOT NULL)
                )
                AND threshold_state = 'NOT_APPLICABLE'
            )
            OR (
                notification_mode = 'TARGET_PRICE'
                AND target_price_minor > 0
                AND notification_reference_price_minor IS NULL
                AND threshold_state IN ('UNKNOWN', 'ABOVE_TARGET', 'REACHED_NOTIFIED')
            )
        );
