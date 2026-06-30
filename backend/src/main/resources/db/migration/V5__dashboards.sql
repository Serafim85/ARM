CREATE TABLE IF NOT EXISTS dashboards (
  id           BIGSERIAL    PRIMARY KEY,
  owner_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  visibility   VARCHAR(32)  NOT NULL,
  name         VARCHAR(255) NOT NULL,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_dashboards_visibility CHECK (visibility IN ('PRIVATE', 'SHARED'))
);

CREATE INDEX IF NOT EXISTS idx_dashboards_owner_id ON dashboards (owner_id);

CREATE TABLE IF NOT EXISTS dashboard_shared_users (
  dashboard_id BIGINT NOT NULL REFERENCES dashboards (id) ON DELETE CASCADE,
  user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  PRIMARY KEY (dashboard_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_dashboard_shared_users_user_id ON dashboard_shared_users (user_id);

CREATE TABLE IF NOT EXISTS dashboard_widgets (
  id            BIGSERIAL   PRIMARY KEY,
  widget_type   VARCHAR(50) NOT NULL,
  dashboard_id  BIGINT      NOT NULL REFERENCES dashboards (id) ON DELETE CASCADE,
  sort_order    INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_dashboard_widgets_dashboard_id ON dashboard_widgets (dashboard_id);
