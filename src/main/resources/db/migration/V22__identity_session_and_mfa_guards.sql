-- Additive security state. Existing access JWTs without authVersion must sign in again.
ALTER TABLE users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN mfa_secret_ciphertext VARCHAR(512);
ALTER TABLE users ADD COLUMN mfa_last_accepted_step BIGINT NOT NULL DEFAULT -1;
ALTER TABLE refresh_tokens ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE refresh_tokens ADD COLUMN mfa_verified_at TIMESTAMPTZ;

CREATE TABLE mfa_challenges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('ENROLL', 'VERIFY')),
    auth_version BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    pending_secret VARCHAR(512),
    guest_cart_token VARCHAR(255),
    login_method VARCHAR(16) NOT NULL
);
CREATE INDEX idx_mfa_challenges_user ON mfa_challenges(user_id);
CREATE INDEX idx_mfa_challenges_expiry ON mfa_challenges(expires_at);
CREATE TABLE mfa_recovery_codes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL UNIQUE,
    used_at TIMESTAMPTZ
);
CREATE INDEX idx_mfa_recovery_user ON mfa_recovery_codes(user_id);
