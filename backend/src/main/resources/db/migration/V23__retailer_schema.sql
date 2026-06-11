-- Phase 23 (R2 / FR-01): Retailer self-service onboarding portal.
-- A retailer is a B2B SaaS tenant that self-registers, then passes the 3-gate
-- approval pattern (ops -> compliance -> admin). On full approval the retailer
-- is ACTIVATED, an API key is issued (SHA-256 hash stored for lookup), and a
-- billing account is created for its hub (ties FR-01 -> FR-06).

CREATE TABLE IF NOT EXISTS oltp.retailers (
    retailer_id          VARCHAR(50)  PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    contact_email        VARCHAR(255) NOT NULL,
    store_id             VARCHAR(50)  REFERENCES oltp.dark_stores(store_id) ON DELETE SET NULL,
    tier                 VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    approval_ops         BOOLEAN      NOT NULL DEFAULT FALSE,
    approval_compliance  BOOLEAN      NOT NULL DEFAULT FALSE,
    approval_admin       BOOLEAN      NOT NULL DEFAULT FALSE,
    api_key_hash         VARCHAR(64),
    billing_account_id   VARCHAR(50),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT retailers_tier_chk   CHECK (tier   IN ('BASIC','PRO','ENTERPRISE')),
    CONSTRAINT retailers_status_chk CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_retailers_api_key_hash ON oltp.retailers (api_key_hash);
CREATE INDEX IF NOT EXISTS idx_retailers_status ON oltp.retailers (status);
