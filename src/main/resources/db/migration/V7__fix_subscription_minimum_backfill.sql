WITH active_subscription_history AS (
    SELECT
        subscription.id AS subscription_id,
        MIN(snapshot.regular_price_minor) AS minimum_regular_price_minor,
        MAX(snapshot.observed_at) AS latest_regular_price_observed_at
    FROM subscriptions subscription
    JOIN price_snapshots snapshot
      ON snapshot.watch_target_id = subscription.watch_target_id
     AND snapshot.observed_at >= subscription.created_at - INTERVAL '15 minutes'
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
