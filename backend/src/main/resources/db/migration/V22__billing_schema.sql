-- Phase 22 (R2 / FR-06): Billing engine.
-- Flat-tier subscription billing per active hub. A billing_account ties a hub
-- (dark store today; extensible to retailer tenants under FR-01) to a flat-fee
-- tier; invoices are generated per billing period at the tier's flat rate.

CREATE TABLE IF NOT EXISTS oltp.billing_accounts (
    account_id   VARCHAR(50)  PRIMARY KEY,
    store_id     VARCHAR(50)  NOT NULL REFERENCES oltp.dark_stores(store_id) ON DELETE CASCADE,
    tier         VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT billing_accounts_tier_chk   CHECK (tier   IN ('BASIC','PRO','ENTERPRISE')),
    CONSTRAINT billing_accounts_status_chk CHECK (status IN ('ACTIVE','SUSPENDED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_billing_accounts_store ON oltp.billing_accounts (store_id);

CREATE TABLE IF NOT EXISTS oltp.invoices (
    invoice_id    VARCHAR(50)   PRIMARY KEY,
    account_id    VARCHAR(50)   NOT NULL REFERENCES oltp.billing_accounts(account_id) ON DELETE CASCADE,
    period_start  DATE          NOT NULL,
    period_end    DATE          NOT NULL,
    amount        NUMERIC(10,2) NOT NULL CHECK (amount >= 0.00),
    currency      VARCHAR(3)    NOT NULL DEFAULT 'CHF',
    status        VARCHAR(20)   NOT NULL DEFAULT 'ISSUED',
    issued_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT invoices_status_chk CHECK (status IN ('DRAFT','ISSUED','PAID','VOID')),
    CONSTRAINT invoices_period_chk CHECK (period_end >= period_start)
);

CREATE INDEX IF NOT EXISTS idx_invoices_account ON oltp.invoices (account_id);
