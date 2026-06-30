ALTER TABLE users
  ADD COLUMN IF NOT EXISTS chart_ui_preferences_json TEXT;
