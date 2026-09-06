CREATE TABLE notification_fanout_jobs (
    snapshot_id UUID PRIMARY KEY REFERENCES price_snapshots(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_notification_fanout_status
        CHECK (status IN ('PENDING', 'RETRY', 'DONE')),
    CONSTRAINT ck_notification_fanout_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_notification_fanout_claim
        CHECK ((claim_token IS NULL) = (claim_until IS NULL)),
    CONSTRAINT ck_notification_fanout_completion
        CHECK (
            (status = 'DONE' AND completed_at IS NOT NULL
                AND claim_token IS NULL AND claim_until IS NULL)
            OR
            (status IN ('PENDING', 'RETRY') AND completed_at IS NULL)
        )
);

CREATE INDEX idx_notification_fanout_jobs_due
    ON notification_fanout_jobs (next_attempt_at, snapshot_id)
    WHERE status IN ('PENDING', 'RETRY');
