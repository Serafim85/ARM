ALTER TABLE notification_subscriptions
  ADD COLUMN IF NOT EXISTS owner_email VARCHAR(255);

UPDATE notification_subscriptions
SET owner_email = recipient_email
WHERE owner_email IS NULL OR owner_email = '';

ALTER TABLE notification_subscriptions
  ALTER COLUMN owner_email SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notification_subscriptions_owner_email
  ON notification_subscriptions (owner_email);
