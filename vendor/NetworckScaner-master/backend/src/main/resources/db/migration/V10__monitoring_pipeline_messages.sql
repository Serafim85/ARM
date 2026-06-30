CREATE TABLE IF NOT EXISTS monitoring_pipeline_messages (
  message_id VARCHAR(64) NOT NULL,
  stage VARCHAR(32) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (message_id, stage)
);
