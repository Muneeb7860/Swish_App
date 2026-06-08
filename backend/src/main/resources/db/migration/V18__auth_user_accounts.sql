-- Phase 16: Auth — user accounts and sessions
-- Owns: user_accounts, sessions
-- Notes:
--   • Password values are stored as BCrypt-encoded hashes (60 chars, prefix $2a$/$2b$/$2y$).
--   • Sessions are server-side, even though stateless JWTs are used; this lets us
--     revoke a single session without rotating the JWT signing key.

CREATE TABLE IF NOT EXISTS oltp.user_accounts (
    user_id          VARCHAR(36)  PRIMARY KEY,
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_accounts_status_chk
        CHECK (status IN ('ACTIVE', 'LOCKED', 'PENDING'))
);

CREATE INDEX IF NOT EXISTS idx_user_accounts_email ON oltp.user_accounts (email);

CREATE TABLE IF NOT EXISTS oltp.sessions (
    session_id          VARCHAR(36)  PRIMARY KEY,
    user_id             VARCHAR(36)  NOT NULL REFERENCES oltp.user_accounts(user_id),
    device_fingerprint  VARCHAR(255),
    ip_address          VARCHAR(45),
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON oltp.sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_active_expires ON oltp.sessions (active, expires_at);
