ALTER TABLE user_profiles
    ADD COLUMN active_subscription_limit INTEGER NOT NULL DEFAULT 10,
    ADD CONSTRAINT ck_user_profiles_active_subscription_limit
        CHECK (active_subscription_limit > 0);
