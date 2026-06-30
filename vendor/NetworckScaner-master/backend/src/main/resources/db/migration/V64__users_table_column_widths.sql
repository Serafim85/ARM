ALTER TABLE users
    ADD COLUMN IF NOT EXISTS table_column_widths_json TEXT;
