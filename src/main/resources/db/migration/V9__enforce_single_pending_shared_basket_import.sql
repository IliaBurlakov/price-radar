DELETE FROM pending_shared_basket_imports older
USING pending_shared_basket_imports newer
WHERE older.user_id = newer.user_id
  AND (
      older.created_at < newer.created_at
      OR (older.created_at = newer.created_at AND older.id < newer.id)
  );

ALTER TABLE pending_shared_basket_imports
    ADD CONSTRAINT uk_pending_shared_basket_import_user UNIQUE (user_id);
