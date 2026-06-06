-- =========================================================================================
-- V6__system_alignment_fixes.sql
-- Module: Backend
-- Purpose: Schema updates aligning tables with feedbacks, SOS routing, and IoT telemetry
-- =========================================================================================

-- 1. Create feedbacks table supporting separate rider, store, and product ratings
CREATE TABLE oltp.feedbacks (
    id SERIAL PRIMARY KEY,
    order_id INT UNIQUE REFERENCES oltp.orders(order_id) ON DELETE CASCADE,
    rider_rating INT NOT NULL CHECK (rider_rating BETWEEN 1 AND 5),
    store_rating INT NOT NULL CHECK (store_rating BETWEEN 1 AND 5),
    product_rating INT NOT NULL CHECK (product_rating BETWEEN 1 AND 5),
    comments TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_feedbacks_order ON oltp.feedbacks (order_id);

-- 2. Create transit_incidents table to support the SOS accident routing
CREATE TABLE oltp.transit_incidents (
    incident_id SERIAL PRIMARY KEY,
    order_id INT REFERENCES oltp.orders(order_id) ON DELETE CASCADE,
    rider_id VARCHAR(50) REFERENCES oltp.riders(rider_id) ON DELETE SET NULL,
    incident_type VARCHAR(20) NOT NULL CHECK (incident_type IN ('ACCIDENT', 'BREAKDOWN', 'WEATHER_HALT')),
    gps_latitude NUMERIC(9, 6) NOT NULL,
    gps_longitude NUMERIC(9, 6) NOT NULL,
    insurance_claim_registered BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'REPORTED' CHECK (status IN ('REPORTED', 'RESOLVED', 'CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transit_incidents_order ON oltp.transit_incidents (order_id);
CREATE INDEX idx_transit_incidents_rider ON oltp.transit_incidents (rider_id);

-- 3. Create wastage_logs table for expired or damaged items write-offs
CREATE TABLE oltp.wastage_logs (
    wastage_id SERIAL PRIMARY KEY,
    store_id VARCHAR(50) REFERENCES oltp.dark_stores(store_id) ON DELETE CASCADE,
    item_id VARCHAR(50) REFERENCES oltp.inventory(item_id) ON DELETE CASCADE,
    qty_wasted INT NOT NULL CHECK (qty_wasted > 0),
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('EXPIRED', 'DAMAGED', 'TRANSIT_LOSS', 'CUSTOMER_REJECTED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wastage_logs_store_item ON oltp.wastage_logs (store_id, item_id);

-- 4. Alter orders table to include dynamic SLAs and store-fault waivers
ALTER TABLE oltp.orders ADD COLUMN promised_by TIMESTAMP WITH TIME ZONE;
ALTER TABLE oltp.orders ADD COLUMN contains_perishables BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE oltp.orders ADD COLUMN min_cart_value_met BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE oltp.orders ADD COLUMN store_fault_waiver_applied BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE oltp.orders ADD COLUMN perishable_maintenance_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (perishable_maintenance_fee >= 0.00);
ALTER TABLE oltp.orders ADD COLUMN price_locked_at TIMESTAMP WITH TIME ZONE;

-- 5. Alter riders table to track Cash-on-Delivery shift limits
ALTER TABLE oltp.riders ADD COLUMN cash_collected_limit NUMERIC(10, 2) NOT NULL DEFAULT 100.00 CHECK (cash_collected_limit >= 0.00);
ALTER TABLE oltp.riders ADD COLUMN current_cash_in_hand NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (current_cash_in_hand >= 0.00);
ALTER TABLE oltp.riders ADD COLUMN active_shift_id VARCHAR(50);

-- 6. Alter dark_stores table to record IoT temperature parameters and audits
ALTER TABLE oltp.dark_stores ADD COLUMN freezer_temp_celsius NUMERIC(4, 1);
ALTER TABLE oltp.dark_stores ADD COLUMN chiller_temp_celsius NUMERIC(4, 1);
ALTER TABLE oltp.dark_stores ADD COLUMN last_iot_heartbeat TIMESTAMP WITH TIME ZONE;
ALTER TABLE oltp.dark_stores ADD COLUMN last_sanitization_audit TIMESTAMP WITH TIME ZONE;
