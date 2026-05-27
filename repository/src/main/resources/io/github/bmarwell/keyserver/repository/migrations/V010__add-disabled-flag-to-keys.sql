-- Add keyserver-level 'disabled' flag to the keys table.
-- A disabled key is hidden from op=index and op=get results but is not revoked
-- in the OpenPGP sense; the flag is set by keyserver administrators only.
-- Defaults to FALSE so all existing keys remain visible after the migration.
ALTER TABLE keys ADD COLUMN disabled BOOLEAN NOT NULL DEFAULT FALSE;
