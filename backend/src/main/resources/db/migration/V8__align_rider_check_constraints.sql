-- =========================================================================================
-- V8__align_rider_check_constraints.sql
-- Module: Backend
-- Purpose: Relax check constraints on oltp.riders to support additional vehicle types and
-- onboarding status transitions from application code.
-- =========================================================================================

-- 1. Drop existing constraints if they exist (standard naming is riders_vehicle_type_check & riders_onboarding_status_check)
ALTER TABLE oltp.riders DROP CONSTRAINT IF EXISTS riders_vehicle_type_check;
ALTER TABLE oltp.riders DROP CONSTRAINT IF EXISTS riders_onboarding_status_check;

-- 2. Re-create vehicle_type constraint to allow E-Bike, Scooter, Bicycle, Motorbike, Van (case-insensitively)
ALTER TABLE oltp.riders ADD CONSTRAINT riders_vehicle_type_check 
    CHECK (LOWER(vehicle_type) IN ('e-bike', 'scooter', 'bicycle', 'motorbike', 'van', 'bike'));

-- 3. Re-create onboarding_status constraint to support pending_review and approved states
ALTER TABLE oltp.riders ADD CONSTRAINT riders_onboarding_status_check 
    CHECK (onboarding_status IN ('unapplied', 'pending', 'active', 'pending_review', 'approved'));
