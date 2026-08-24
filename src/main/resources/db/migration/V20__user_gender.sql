-- Optional self-selected gender on the user profile (USER-06).
-- Nullable + additive, so old images stay schema-compatible (expand-contract).
ALTER TABLE users ADD COLUMN gender VARCHAR(16);
