ALTER TABLE user_profiles
    ADD COLUMN region_selected BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_profiles
SET region_selected = TRUE;