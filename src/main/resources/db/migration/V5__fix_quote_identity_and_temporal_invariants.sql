DELETE FROM telegram_pending_target_prices;

UPDATE watch_targets
SET city_name = CASE dest
    WHEN 1259570991 THEN 'Moscow'
    WHEN -366519 THEN 'Novosibirsk'
    ELSE 'Unknown region'
END
WHERE city_name = 'Moscow'
  AND created_at <= (
      SELECT installed_on
      FROM flyway_schema_history
      WHERE version = '4'
        AND success = TRUE
      ORDER BY installed_rank DESC
      LIMIT 1
  );

ALTER TABLE telegram_pending_target_prices
    ADD COLUMN quote_snapshot_id UUID;

ALTER TABLE telegram_pending_target_prices
    DROP COLUMN watch_target_id;

ALTER TABLE telegram_pending_target_prices
    ALTER COLUMN quote_snapshot_id SET NOT NULL,
    ADD CONSTRAINT fk_telegram_pending_target_prices_quote_snapshot
        FOREIGN KEY (quote_snapshot_id) REFERENCES price_snapshots(id) ON DELETE CASCADE;

UPDATE subscriptions
SET baseline_price_minor = NULL,
    baseline_observed_at = NULL
WHERE baseline_observed_at < created_at - INTERVAL '15 minutes';

UPDATE subscriptions
SET threshold_state = 'UNKNOWN',
    threshold_observed_at = NULL
WHERE notification_mode = 'TARGET_PRICE'
  AND threshold_observed_at < created_at - INTERVAL '15 minutes';

ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_baseline_within_quote_ttl
        CHECK (
            baseline_observed_at IS NULL
            OR baseline_observed_at >= created_at - INTERVAL '15 minutes'
        ),
    ADD CONSTRAINT ck_subscriptions_threshold_within_quote_ttl
        CHECK (
            threshold_observed_at IS NULL
            OR threshold_observed_at >= created_at - INTERVAL '15 minutes'
        );
