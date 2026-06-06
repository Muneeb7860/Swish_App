-- =========================================================================================
-- V7__logistics_and_dispatch_schema.sql
-- Module: Backend
-- Purpose: Schema setup for Logistics, Routing & Dispatch Bounded Context (Domain 4)
-- =========================================================================================

CREATE SCHEMA IF NOT EXISTS dispatch;

-- 1. Vehicle Configurations and capacities
CREATE TABLE dispatch.vehicle_configs (
    vehicle_type VARCHAR(50) PRIMARY KEY,
    max_weight_kg NUMERIC(5, 2) NOT NULL CHECK (max_weight_kg > 0.00),
    average_speed_kmh NUMERIC(5, 2) NOT NULL CHECK (average_speed_kmh > 0.00)
);

-- Seed default vehicle capacities
INSERT INTO dispatch.vehicle_configs (vehicle_type, max_weight_kg, average_speed_kmh) VALUES
('E-Bike', 10.00, 20.00),
('Bicycle', 8.00, 15.00),
('Scooter', 25.00, 40.00),
('Motorbike', 30.00, 45.00),
('Van', 250.00, 50.00)
ON CONFLICT (vehicle_type) DO UPDATE SET 
    max_weight_kg = EXCLUDED.max_weight_kg,
    average_speed_kmh = EXCLUDED.average_speed_kmh;

-- 2. Rider Daily Gear Scan logs (Thermal Bag & Helmet checks)
CREATE TABLE dispatch.gear_scans (
    scan_id VARCHAR(50) PRIMARY KEY,
    rider_id VARCHAR(50) NOT NULL REFERENCES oltp.riders(rider_id) ON DELETE CASCADE,
    scan_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    gear_type VARCHAR(20) NOT NULL CHECK (gear_type IN ('THERMAL_BAG', 'HELMET')),
    verification_status VARCHAR(20) NOT NULL CHECK (verification_status IN ('PASSED', 'FAILED', 'PENDING')),
    image_url TEXT,
    checked_by VARCHAR(50)
);

CREATE INDEX idx_gear_scans_rider ON dispatch.gear_scans (rider_id, scan_time DESC);

-- 3. Real-time Active Shipment Tracker
CREATE TABLE dispatch.active_shipments (
    shipment_id VARCHAR(50) PRIMARY KEY,
    order_id INT UNIQUE NOT NULL REFERENCES oltp.orders(order_id) ON DELETE CASCADE,
    rider_id VARCHAR(50) REFERENCES oltp.riders(rider_id) ON DELETE SET NULL,
    status VARCHAR(25) NOT NULL CHECK (status IN ('ASSIGNED', 'PICKING_UP', 'DELIVERING', 'COMPLETED', 'REALLOCATED')),
    total_weight_kg NUMERIC(6, 2) NOT NULL DEFAULT 1.00 CHECK (total_weight_kg > 0.00),
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_gps_update TIMESTAMP WITH TIME ZONE,
    last_lat NUMERIC(9, 6),
    last_lng NUMERIC(9, 6),
    stationary_since TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_active_shipments_rider ON dispatch.active_shipments (rider_id, status);
CREATE INDEX idx_active_shipments_stationary ON dispatch.active_shipments (status, stationary_since) WHERE stationary_since IS NOT NULL;

-- 4. Hardened Matching Eligibility Function (Capacity, Gear verification, Anti-Fraud matching)
CREATE OR REPLACE FUNCTION dispatch.is_rider_eligible_for_matching(
    p_rider_id VARCHAR(50),
    p_order_id INT,
    p_order_weight_kg NUMERIC(6, 2)
) RETURNS BOOLEAN AS $$
DECLARE
    v_vehicle_type VARCHAR(50);
    v_max_weight NUMERIC(5, 2);
    v_has_valid_gear BOOLEAN;
    v_customer_id VARCHAR(50);
    v_customer_name VARCHAR(100);
    v_rider_name VARCHAR(100);
BEGIN
    -- 1. Fetch Rider Vehicle details & max capacity
    SELECT r.vehicle_type, r.full_name, vc.max_weight_kg 
    INTO v_vehicle_type, v_rider_name, v_max_weight
    FROM oltp.riders r
    JOIN dispatch.vehicle_configs vc ON r.vehicle_type = vc.vehicle_type
    WHERE r.rider_id = p_rider_id AND r.onboarding_status = 'active';

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- 2. Weight capacity check
    IF p_order_weight_kg > v_max_weight THEN
        RETURN FALSE;
    END IF;

    -- 3. Verify gear scan check (must have passed scan for THERMAL_BAG within last 24h)
    SELECT EXISTS (
        SELECT 1 FROM dispatch.gear_scans
        WHERE rider_id = p_rider_id 
          AND gear_type = 'THERMAL_BAG'
          AND verification_status = 'PASSED'
          AND scan_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
    ) INTO v_has_valid_gear;

    IF v_has_valid_gear = FALSE THEN
        RETURN FALSE;
    END IF;

    -- 4. Anti-Self-Matching/Fraud Audit (Customer matching rider ID or Name triggers fraud block)
    SELECT customer_id, full_name INTO v_customer_id, v_customer_name
    FROM oltp.customers
    WHERE customer_id = (SELECT customer_id FROM oltp.orders WHERE order_id = p_order_id);

    IF LOWER(v_customer_name) = LOWER(v_rider_name) OR v_customer_id = p_rider_id THEN
        RETURN FALSE;
    END IF;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;
