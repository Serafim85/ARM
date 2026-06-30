ALTER TABLE notification_subscriptions
  ADD COLUMN IF NOT EXISTS subscription_type VARCHAR(32);

UPDATE notification_subscriptions
SET subscription_type = CASE
  WHEN UPPER(COALESCE(notification_kind, '')) = 'ADMIN' THEN 'SYSTEM'
  ELSE 'DEVICE'
END
WHERE subscription_type IS NULL OR subscription_type = '';

ALTER TABLE notification_subscriptions
  ALTER COLUMN subscription_type SET NOT NULL;
