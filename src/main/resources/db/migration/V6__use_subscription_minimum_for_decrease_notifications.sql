ALTER TABLE subscriptions
    DROP CONSTRAINT ck_subscriptions_baseline_pair,
    DROP CONSTRAINT ck_subscriptions_notification_state,
    DROP CONSTRAINT ck_subscriptions_baseline_within_quote_ttl;

ALTER TABLE subscriptions
    RENAME COLUMN baseline_price_minor TO notification_reference_price_minor;

ALTER TABLE subscriptions
    RENAME COLUMN baseline_observed_at TO last_processed_price_observed_at;

WITH active_subscription_history AS (
    SELECT
        subscription.id AS subscription_id,
        MIN(snapshot.regular_price_minor) AS minimum_regular_price_minor,
        MAX(snapshot.observed_at) AS latest_regular_price_observed_at
    FROM subscriptions subscription
    JOIN price_snapshots snapshot
      ON snapshot.watch_target_id = subscription.watch_target_id
     AND snapshot.observed_at >= subscription.created_at
     AND snapshot.observed_at <= CURRENT_TIMESTAMP
     AND snapshot.status = 'REGULAR_PRICE'
     AND snapshot.price_source = 'PRODUCT'
     AND snapshot.regular_price_minor > 0
    WHERE subscription.status = 'ACTIVE'
      AND subscription.notification_mode = 'ANY_DECREASE'
    GROUP BY subscription.id
)
UPDATE subscriptions subscription
SET notification_reference_price_minor = history.minimum_regular_price_minor,
    last_processed_price_observed_at = history.latest_regular_price_observed_at
FROM active_subscription_history history
WHERE subscription.id = history.subscription_id;

ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_notification_reference_pair
        CHECK (
            (
                notification_reference_price_minor IS NULL
                AND last_processed_price_observed_at IS NULL
            )
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
                AND threshold_state = 'NOT_APPLICABLE'
            )
            OR (
                notification_mode = 'TARGET_PRICE'
                AND target_price_minor > 0
                AND notification_reference_price_minor IS NULL
                AND last_processed_price_observed_at IS NULL
                AND threshold_state IN ('UNKNOWN', 'ABOVE_TARGET', 'REACHED_NOTIFIED')
            )
        ),
    ADD CONSTRAINT ck_subscriptions_processed_price_within_quote_ttl
        CHECK (
            last_processed_price_observed_at IS NULL
            OR last_processed_price_observed_at >= created_at - INTERVAL '15 minutes'
        );
