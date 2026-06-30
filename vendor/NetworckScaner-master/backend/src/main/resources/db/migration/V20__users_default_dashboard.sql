ALTER TABLE users
  ADD COLUMN IF NOT EXISTS default_dashboard_id BIGINT;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_users_default_dashboard'
  ) THEN
    ALTER TABLE users
      ADD CONSTRAINT fk_users_default_dashboard
      FOREIGN KEY (default_dashboard_id)
      REFERENCES dashboards (id)
      ON DELETE SET NULL;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_default_dashboard_id
  ON users (default_dashboard_id);
