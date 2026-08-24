-- Track how an account authenticates. Existing rows are local (email + password).
-- Additive + backfilled default so old images stay schema-compatible (expand-contract).
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
