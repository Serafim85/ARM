CREATE TABLE IF NOT EXISTS topologies (
  id             BIGSERIAL    PRIMARY KEY,
  owner_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  visibility     VARCHAR(32)  NOT NULL,
  name           VARCHAR(255) NOT NULL,
  document_json  TEXT         NOT NULL DEFAULT '{}',
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_topologies_visibility CHECK (visibility IN ('PRIVATE', 'SHARED'))
);

CREATE INDEX IF NOT EXISTS idx_topologies_owner_id ON topologies (owner_id);

CREATE TABLE IF NOT EXISTS topology_shared_users (
  topology_id BIGINT NOT NULL REFERENCES topologies (id) ON DELETE CASCADE,
  user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  PRIMARY KEY (topology_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_topology_shared_users_user_id ON topology_shared_users (user_id);
