CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profiles(id),
    watch_target_id UUID NOT NULL REFERENCES watch_targets(id),
    notification_mode VARCHAR(32) NOT NULL,
    target_price_minor BIGINT,
    baseline_price_minor BIGINT,
    baseline_observed_at TIMESTAMPTZ,
    threshold_state VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_subscriptions_notification_mode
        CHECK (notification_mode IN ('ANY_DECREASE', 'TARGET_PRICE')),
    CONSTRAINT ck_subscriptions_threshold_state
        CHECK (threshold_state IN ('NOT_APPLICABLE', 'UNKNOWN', 'ABOVE_TARGET', 'REACHED_NOTIFIED')),
    CONSTRAINT ck_subscriptions_status
        CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_subscriptions_baseline_pair
        CHECK (
            (baseline_price_minor IS NULL AND baseline_observed_at IS NULL)
            OR (baseline_price_minor > 0 AND baseline_observed_at IS NOT NULL)
        ),
    CONSTRAINT ck_subscriptions_notification_state
        CHECK (
            (
                notification_mode = 'ANY_DECREASE'
                AND target_price_minor IS NULL
                AND threshold_state = 'NOT_APPLICABLE'
            )
            OR (
                notification_mode = 'TARGET_PRICE'
                AND target_price_minor > 0
                AND baseline_price_minor IS NULL
                AND baseline_observed_at IS NULL
                AND threshold_state IN ('UNKNOWN', 'ABOVE_TARGET', 'REACHED_NOTIFIED')
            )
        ),
    CONSTRAINT ck_subscriptions_lifecycle
        CHECK (
            (status = 'ACTIVE' AND ended_at IS NULL)
            OR (status = 'ENDED' AND ended_at IS NOT NULL AND ended_at >= created_at)
        )
);

CREATE UNIQUE INDEX uk_subscriptions_active_user_target
    ON subscriptions (user_id, watch_target_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_subscriptions_watch_target_status
    ON subscriptions (watch_target_id, status);

CREATE INDEX idx_subscriptions_user_status_created
    ON subscriptions (user_id, status, created_at DESC);
