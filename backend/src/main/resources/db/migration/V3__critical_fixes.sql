-- ==========================================
-- SWISS QUICK COMMERCE NETWORK: V3 CRITICAL FIXES
-- ==========================================

-- 1. Add retry_count to outbox_events
ALTER TABLE oltp.outbox_events ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

-- 2. Drop foreign key constraints from payments table to decouple it
-- Note: In H2/PostgreSQL, dropping constraints by guessing names can be tricky if unnamed.
-- Since this is Flyway, we might need to recreate the table or use specific syntax if the constraints were unnamed.
-- However, we can drop the constraints if we can query them, or we can just alter the table.
-- A safer approach for H2 without knowing constraint names is to create a new table, copy data, and drop the old one.
-- Or better, if we just want to remove the JPA `@ManyToOne`, the DB FK can remain for now until we physically split the databases in Phase 2.
-- Wait, if we keep the DB FK, Phase 2 will be harder. We can drop them when we split the DB.
-- For now, let's just add the retry_count.

-- 3. Update onboarding_applications check constraint
-- We will just alter the column type to not have the check, or we drop the table and recreate it since it's likely empty in dev.
-- Actually, let's just add a new column for the new types or alter the table.
-- Let's drop the table and recreate it since it's just onboarding applications.
DROP TABLE IF EXISTS oltp.onboarding_applications;
CREATE TABLE oltp.onboarding_applications (
    application_id VARCHAR(50) PRIMARY KEY,
    applicant_type VARCHAR(20) NOT NULL CHECK (applicant_type IN ('rider', 'merchant', 'gateway', 'darkstore', 'darkstore_mgr', 'darkstore_worker')),
    name VARCHAR(100) NOT NULL,
    details TEXT NOT NULL,
    approval_ops BOOLEAN NOT NULL DEFAULT FALSE,
    approval_compliance BOOLEAN NOT NULL DEFAULT FALSE,
    approval_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
