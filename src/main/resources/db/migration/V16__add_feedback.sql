CREATE TABLE feedback_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profiles(id),
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_feedback_messages_not_blank CHECK (btrim(message) <> ''),
    CONSTRAINT ck_feedback_messages_length CHECK (char_length(message) <= 3000)
);

CREATE INDEX idx_feedback_messages_user_created
    ON feedback_messages (user_id, created_at DESC);

CREATE TABLE pending_feedback_inputs (
    user_id UUID PRIMARY KEY REFERENCES user_profiles(id) ON DELETE CASCADE,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    chat_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pending_feedback_telegram_user CHECK (telegram_user_id > 0),
    CONSTRAINT ck_pending_feedback_chat CHECK (chat_id > 0),
    CONSTRAINT ck_pending_feedback_lifecycle CHECK (expires_at > created_at)
);

CREATE INDEX idx_pending_feedback_inputs_expiry
    ON pending_feedback_inputs (expires_at);
