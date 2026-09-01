UPDATE user_profiles
SET wallet_discount_percent = 3
WHERE wallet_discount_percent NOT BETWEEN 2 AND 20;

ALTER TABLE user_profiles
    DROP CONSTRAINT ck_user_profiles_wallet_discount,
    ADD CONSTRAINT ck_user_profiles_wallet_discount
        CHECK (wallet_discount_percent BETWEEN 2 AND 20);

CREATE TABLE pending_wallet_discount_inputs (
    user_id UUID PRIMARY KEY REFERENCES user_profiles(id) ON DELETE CASCADE,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    chat_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pending_wallet_discount_telegram_user CHECK (telegram_user_id > 0),
    CONSTRAINT ck_pending_wallet_discount_chat CHECK (chat_id > 0),
    CONSTRAINT ck_pending_wallet_discount_lifecycle CHECK (expires_at > created_at)
);

CREATE INDEX idx_pending_wallet_discount_inputs_expiry
    ON pending_wallet_discount_inputs (expires_at);
