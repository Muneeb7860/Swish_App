-- Phase 20: Add role column to user_accounts
-- Allows users to hold CUSTOMER, ADMIN, RIDER, or WHOLESALER roles.
-- All existing accounts default to CUSTOMER.
-- The role is embedded in the JWT at login time so the auth filter
-- can grant the correct Spring Security authority without a DB lookup.

ALTER TABLE oltp.user_accounts
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE oltp.user_accounts
    ADD CONSTRAINT user_accounts_role_chk
        CHECK (role IN ('CUSTOMER', 'ADMIN', 'RIDER', 'WHOLESALER'));

CREATE INDEX IF NOT EXISTS idx_user_accounts_role ON oltp.user_accounts (role);
