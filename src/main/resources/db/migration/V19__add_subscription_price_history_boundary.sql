ALTER TABLE subscriptions
    ADD COLUMN price_history_started_at TIMESTAMPTZ;

UPDATE subscriptions
SET price_history_started_at = created_at;

ALTER TABLE subscriptions
    ALTER COLUMN price_history_started_at SET NOT NULL,
    ADD CONSTRAINT ck_subscriptions_price_history_boundary
        CHECK (price_history_started_at <= created_at);
