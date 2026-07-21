CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    snapshot_id UUID REFERENCES price_snapshots(id),
    notification_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    CONSTRAINT ck_notification_outbox_type
        CHECK (notification_type IN ('PRICE_DECREASE', 'TARGET_REACHED')),
    CONSTRAINT ck_notification_outbox_status
        CHECK (status IN ('PENDING', 'RETRY', 'SENT', 'FAILED')),
    CONSTRAINT ck_notification_outbox_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_notification_outbox_delivery_state
        CHECK (
            (status = 'SENT' AND sent_at IS NOT NULL)
            OR (status <> 'SENT' AND sent_at IS NULL)
        )
);

CREATE INDEX idx_notification_outbox_delivery
    ON notification_outbox (status, next_attempt_at, id);
