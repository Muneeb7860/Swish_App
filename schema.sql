-- ==========================================
-- SWISS QUICK COMMERCE NETWORK: DATABASE SCHEMA
-- Target Engine: PostgreSQL 15+ (OLTP & OLAP Warehouse)
-- ==========================================

-- ==========================================
-- 1. OLTP TRANSACTIONAL SYSTEM SCHEMA
-- ==========================================
CREATE SCHEMA IF NOT EXISTS oltp;

-- Customers table (high-speed lookups with trust score matrix, GDPR anonymization, and probation tracking)
CREATE TABLE oltp.customers (
    customer_id VARCHAR(50) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE, -- Set to NULL on GDPR purge
    hashed_email VARCHAR(64) UNIQUE NOT NULL, -- SHA256 of email, retained post-GDPR purge to prevent fraud
    wallet_balance NUMERIC(10, 2) NOT NULL DEFAULT 100.00 CHECK (wallet_balance >= 0.00),
    loyalty_points INT NOT NULL DEFAULT 0 CHECK (loyalty_points >= 0),
    vip_status BOOLEAN NOT NULL DEFAULT FALSE,
    trust_score INT NOT NULL DEFAULT 100 CHECK (trust_score BETWEEN 0 AND 100),
    is_anonymized BOOLEAN NOT NULL DEFAULT FALSE,
    is_on_probation BOOLEAN NOT NULL DEFAULT FALSE,
    consecutive_orders_completed INT NOT NULL DEFAULT 0 CHECK (consecutive_orders_completed >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Customer Saved Addresses (for geographical and delivery routing validations)
CREATE TABLE oltp.customer_addresses (
    address_id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) REFERENCES oltp.customers(customer_id) ON DELETE CASCADE,
    label VARCHAR(50) NOT NULL, -- e.g., 'Home', 'Work'
    address_line TEXT NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Customer Saved Payment Cards (tokenized for GDPR compliance)
CREATE TABLE oltp.customer_payment_cards (
    card_id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) REFERENCES oltp.customers(customer_id) ON DELETE CASCADE,
    card_type VARCHAR(20) NOT NULL, -- e.g., 'Visa', 'MasterCard'
    last_four_digits VARCHAR(4) NOT NULL,
    token_reference VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Riders table (GPS and status checks with trust score matrix)
CREATE TABLE oltp.riders (
    rider_id VARCHAR(50) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL CHECK (vehicle_type IN ('E-Bike', 'Scooter')),
    onboarding_status VARCHAR(20) NOT NULL DEFAULT 'unapplied' CHECK (onboarding_status IN ('unapplied', 'pending', 'active')),
    wallet_balance NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (wallet_balance >= 0.00),
    active_lat NUMERIC(9, 6),
    active_lng NUMERIC(9, 6),
    trust_score INT NOT NULL DEFAULT 100 CHECK (trust_score BETWEEN 0 AND 100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Rider Academy Certificates
CREATE TABLE oltp.rider_academy_certificates (
    certificate_id SERIAL PRIMARY KEY,
    rider_id VARCHAR(50) REFERENCES oltp.riders(rider_id) ON DELETE CASCADE,
    course_name VARCHAR(100) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Dark Stores table
CREATE TABLE oltp.dark_stores (
    store_id VARCHAR(50) PRIMARY KEY,
    store_name VARCHAR(100) NOT NULL,
    address TEXT NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    storage_capacity_limit INT NOT NULL DEFAULT 5000 CHECK (storage_capacity_limit >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Pickers table (Fulfillment Desk with Speed badges and trust ratings)
CREATE TABLE oltp.pickers (
    picker_id VARCHAR(50) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    trust_score INT NOT NULL DEFAULT 100 CHECK (trust_score BETWEEN 0 AND 100),
    lightning_badge BOOLEAN NOT NULL DEFAULT FALSE,
    active_store_id VARCHAR(50) REFERENCES oltp.dark_stores(store_id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Wholesalers table (B2B restock supplier configuration)
CREATE TABLE oltp.wholesalers (
    wholesaler_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    trust_score INT NOT NULL DEFAULT 100 CHECK (trust_score BETWEEN 0 AND 100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    academy_discount_active BOOLEAN NOT NULL DEFAULT FALSE,
    base_invoice_amount NUMERIC(10, 2) NOT NULL DEFAULT 25.00 CHECK (base_invoice_amount >= 0.00),
    fallback_invoice_amount NUMERIC(10, 2) NOT NULL DEFAULT 35.00 CHECK (fallback_invoice_amount >= 0.00),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Inventory items (SLA stock checks)
CREATE TABLE oltp.inventory (
    item_id VARCHAR(50) PRIMARY KEY,
    store_id VARCHAR(50) REFERENCES oltp.dark_stores(store_id),
    name VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0.00),
    stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    category VARCHAR(50) NOT NULL,
    emoji VARCHAR(10) NOT NULL,
    perishable BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Orders table (OLTP checkout writes with idempotency keys and ESG offsets)
CREATE TABLE oltp.orders (
    order_id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) REFERENCES oltp.customers(customer_id),
    store_id VARCHAR(50) REFERENCES oltp.dark_stores(store_id),
    rider_id VARCHAR(50) REFERENCES oltp.riders(rider_id),
    total_amount NUMERIC(10, 2) NOT NULL CHECK (total_amount >= 0.00),
    tip_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (tip_amount >= 0.00),
    weather_surcharge NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (weather_surcharge >= 0.00),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('Wallet', 'Swipe', 'PayPal', 'Paytm', 'Cash on Delivery')),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picking', 'picked', 'shipping', 'delivered', 'spoiled', 'cancelled')),
    sla_countdown_sec INT NOT NULL DEFAULT 540,
    bags_returned INT NOT NULL DEFAULT 0 CHECK (bags_returned >= 0),
    idempotency_key VARCHAR(100) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Order Items
CREATE TABLE oltp.order_items (
    order_id INT REFERENCES oltp.orders(order_id) ON DELETE CASCADE,
    item_id VARCHAR(50) REFERENCES oltp.inventory(item_id),
    quantity INT NOT NULL CHECK (quantity > 0),
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0.00),
    PRIMARY KEY (order_id, item_id)
);

-- B2B Restock Orders (with status track and idempotency keys)
CREATE TABLE oltp.b2b_restock_orders (
    restock_order_id SERIAL PRIMARY KEY,
    store_id VARCHAR(50) REFERENCES oltp.dark_stores(store_id),
    wholesaler_id VARCHAR(50) REFERENCES oltp.wholesalers(wholesaler_id),
    invoice_amount NUMERIC(10, 2) NOT NULL CHECK (invoice_amount >= 0.00),
    is_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'fulfilled', 'failed')),
    idempotency_key VARCHAR(100) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Real-time IoT Cold Chain and GPS Telemetry logs (with device clock vs server clock logic)
CREATE TABLE oltp.order_telemetry_logs (
    log_id SERIAL PRIMARY KEY,
    order_id INT REFERENCES oltp.orders(order_id) ON DELETE CASCADE,
    device_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    server_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    temperature NUMERIC(4, 1) NOT NULL,
    dry_ice_injected BOOLEAN NOT NULL DEFAULT FALSE,
    alert_triggered BOOLEAN NOT NULL DEFAULT FALSE
);

-- Indexes for telemetry chronology queries (optimized for latest status coordinates)
CREATE INDEX idx_telemetry_order_device_time ON oltp.order_telemetry_logs (order_id, device_timestamp DESC);

-- Double-Entry Auditing Ledger: 1. Journal Entries (with Tamper-Evident Hash Chain)
CREATE TABLE oltp.journal_entries (
    entry_id SERIAL PRIMARY KEY,
    entry_uuid UUID NOT NULL UNIQUE,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    reference VARCHAR(50) NOT NULL, -- e.g. ORDER-PAY, RIDE-EARN, ESG-REBATE, B2B-DEBIT, SYS-SCALING
    description TEXT NOT NULL,
    previous_entry_hash VARCHAR(64),
    entry_hash VARCHAR(64)
);

-- Double-Entry Auditing Ledger: 2. Ledger Lines (Debit/Credit Legs)
CREATE TABLE oltp.ledger_lines (
    line_id SERIAL PRIMARY KEY,
    entry_id INT NOT NULL REFERENCES oltp.journal_entries(entry_id) ON DELETE CASCADE,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('customer', 'rider', 'picker', 'wholesaler', 'system')),
    actor_id VARCHAR(50), -- Polymorphic reference (customer_id, rider_id, picker_id, or wholesaler_id, system is NULL)
    debit NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (debit >= 0.00),
    credit NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (credit >= 0.00),
    CONSTRAINT chk_debit_credit_not_both_zero CHECK (debit > 0.00 OR credit > 0.00),
    CONSTRAINT chk_debit_credit_not_both_positive CHECK (NOT (debit > 0.00 AND credit > 0.00))
);

-- Security Trust Ledger Auditing Delta changes
CREATE TABLE oltp.security_trust_ledger (
    audit_id SERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actor_type VARCHAR(20) NOT NULL CHECK (actor_type IN ('customer', 'rider', 'picker', 'wholesaler', 'system')),
    actor_id VARCHAR(50) NOT NULL,
    event VARCHAR(100) NOT NULL,
    delta INT NOT NULL,
    current_value INT NOT NULL CHECK (current_value BETWEEN 0 AND 100)
);

-- Onboarding Applications
CREATE TABLE oltp.onboarding_applications (
    application_id VARCHAR(50) PRIMARY KEY,
    applicant_type VARCHAR(20) NOT NULL CHECK (applicant_type IN ('rider', 'merchant', 'gateway')),
    name VARCHAR(100) NOT NULL,
    details TEXT NOT NULL,
    approval_ops BOOLEAN NOT NULL DEFAULT FALSE,
    approval_compliance BOOLEAN NOT NULL DEFAULT FALSE,
    approval_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Human-in-the-Loop Approval Queue
CREATE TABLE oltp.hitl_queue (
    ticket_id VARCHAR(50) PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('b2b_payment', 'refund_customer', 'fraud_audit')),
    customer_id VARCHAR(50) REFERENCES oltp.customers(customer_id),
    order_id INT REFERENCES oltp.orders(order_id),
    description TEXT NOT NULL,
    amount NUMERIC(10, 2) NOT NULL CHECK (amount >= 0.00),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'voided')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Dynamic System Configurations table for scaling and parameters
CREATE TABLE oltp.system_configurations (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Chaos Fault Logs for tracking infrastructure injects
CREATE TABLE oltp.chaos_fault_logs (
    fault_id SERIAL PRIMARY KEY,
    fault_type VARCHAR(50) NOT NULL CHECK (fault_type IN ('cold_chain_breakdown', 'wholesaler_outage', 'traffic_congestion')),
    triggered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    details TEXT
);

-- ==========================================
-- 2. OLAP ANALYTICAL DATA WAREHOUSE SCHEMA
-- ==========================================
CREATE SCHEMA IF NOT EXISTS olap;

-- Revenue fact table
CREATE TABLE olap.dw_revenue_facts (
    fact_id SERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    total_sales NUMERIC(10, 2) NOT NULL,
    surcharges NUMERIC(10, 2) NOT NULL DEFAULT 0.00
);

-- Delivery SLA compliance dimension
CREATE TABLE olap.dw_delivery_sla_dimension (
    dimension_id SERIAL PRIMARY KEY,
    order_id INT NOT NULL,
    weather_condition VARCHAR(20) NOT NULL,
    seconds_remaining INT NOT NULL,
    sla_status VARCHAR(20) NOT NULL CHECK (sla_status IN ('met', 'violated')),
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- IoT cold chain temperature alert logs
CREATE TABLE olap.dw_iot_temperature_violations (
    violation_id SERIAL PRIMARY KEY,
    order_id INT NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    max_temperature_recorded NUMERIC(4, 1) NOT NULL,
    alert_triggered TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Customer fraud risk profiles
CREATE TABLE olap.dw_customer_fraud_risk_scores (
    customer_id VARCHAR(50) PRIMARY KEY,
    order_count INT NOT NULL DEFAULT 0,
    refund_count INT NOT NULL DEFAULT 0,
    fraud_risk_percentage INT NOT NULL DEFAULT 0,
    audit_flagged BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ESG bag recycling carbon offset fact table
CREATE TABLE olap.dw_esg_carbon_facts (
    fact_id SERIAL PRIMARY KEY,
    order_id INT NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    bags_returned INT NOT NULL,
    co2_offset_grams INT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ==========================================
-- 3. LEDGER BALANCE CHECK TRIGGER (POSTGRESQL INVARIANT WITH DELETE COVERAGE)
-- ==========================================
CREATE OR REPLACE FUNCTION oltp.check_journal_entry_balance()
RETURNS TRIGGER AS $$
DECLARE
    v_entry_id INT;
    v_sum_debit NUMERIC(10, 2);
    v_sum_credit NUMERIC(10, 2);
BEGIN
    -- Determine target entry depending on mutation type
    IF TG_OP = 'DELETE' THEN
        v_entry_id := OLD.entry_id;
    ELSE
        v_entry_id := NEW.entry_id;
    END IF;

    -- Aggregate the debits and credits for the target journal entry
    SELECT COALESCE(SUM(debit), 0.00), COALESCE(SUM(credit), 0.00)
    INTO v_sum_debit, v_sum_credit
    FROM oltp.ledger_lines
    WHERE entry_id = v_entry_id;

    -- Raise exception if they don't match on transaction commit
    IF v_sum_debit <> v_sum_credit THEN
        RAISE EXCEPTION 'Journal entry with ID % is unbalanced. Total Debits: %, Total Credits: %', 
            v_entry_id, v_sum_debit, v_sum_credit;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Deferrable constraint trigger to check ledger balance at the end of the transaction
CREATE CONSTRAINT TRIGGER trg_enforce_ledger_balance
AFTER INSERT OR UPDATE OR DELETE ON oltp.ledger_lines
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION oltp.check_journal_entry_balance();

-- ==========================================
-- 4. TAMPER-EVIDENT LEDGER HASH CHAIN TRIGGER
-- ==========================================
CREATE OR REPLACE FUNCTION oltp.hash_journal_entry()
RETURNS TRIGGER AS $$
DECLARE
    v_prev_hash VARCHAR(64);
BEGIN
    -- Get the hash of the chronologically last journal entry
    SELECT entry_hash
    INTO v_prev_hash
    FROM oltp.journal_entries
    WHERE entry_id < NEW.entry_id
    ORDER BY entry_id DESC
    LIMIT 1;

    NEW.previous_entry_hash := COALESCE(v_prev_hash, '0000000000000000000000000000000000000000000000000000000000000000');
    
    -- Compute md5 representing the current block hash for ledger integrity
    NEW.entry_hash := MD5(CONCAT(
        NEW.entry_uuid::text,
        NEW.reference,
        NEW.description,
        NEW.previous_entry_hash
    ));

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_hash_journal_entry
BEFORE INSERT ON oltp.journal_entries
FOR EACH ROW
EXECUTE FUNCTION oltp.hash_journal_entry();

-- ==========================================
-- 5. ETL REPLICATION SYNC PROCESS (STORED PROCEDURES)
-- ==========================================
CREATE OR REPLACE PROCEDURE olap.etl_sync_replication_process()
LANGUAGE plpgsql
AS $$
BEGIN
    -- 1. Sync Revenue facts from OLTP orders to OLAP warehouse
    INSERT INTO olap.dw_revenue_facts (timestamp, payment_method, total_sales, surcharges)
    SELECT 
        created_at, 
        payment_method, 
        total_amount, 
        weather_surcharge
    FROM oltp.orders
    WHERE created_at > (SELECT COALESCE(MAX(timestamp), '1970-01-01'::timestamp) FROM olap.dw_revenue_facts);

    -- 2. Sync SLA compliance dimensions
    INSERT INTO olap.dw_delivery_sla_dimension (order_id, weather_condition, seconds_remaining, sla_status, timestamp)
    SELECT 
        o.order_id, 
        CASE 
            WHEN o.weather_surcharge = 0.00 THEN 'Sunny'
            WHEN o.weather_surcharge = 3.00 THEN 'Heavy Rain'
            ELSE 'Thunderstorm'
        END,
        o.sla_countdown_sec,
        CASE WHEN o.sla_countdown_sec > 0 THEN 'met' ELSE 'violated' END,
        o.created_at
    FROM oltp.orders o
    WHERE o.status = 'delivered'
      AND o.order_id NOT IN (SELECT order_id FROM olap.dw_delivery_sla_dimension);

    -- 3. Sync IoT temperature violations from OLTP telemetry logs
    INSERT INTO olap.dw_iot_temperature_violations (order_id, item_name, max_temperature_recorded, alert_triggered)
    SELECT 
        t.order_id,
        i.name,
        MAX(t.temperature) as max_temp,
        MAX(t.server_timestamp)
    FROM oltp.order_telemetry_logs t
    JOIN oltp.order_items oi ON t.order_id = oi.order_id
    JOIN oltp.inventory i ON oi.item_id = i.item_id
    WHERE t.alert_triggered = TRUE AND i.perishable = TRUE
      AND t.order_id NOT IN (SELECT order_id FROM olap.dw_iot_temperature_violations)
    GROUP BY t.order_id, i.name;

    -- 4. Update customer fraud risk scores (skipping anonymized GDPR profiles)
    INSERT INTO olap.dw_customer_fraud_risk_scores (customer_id, order_count, refund_count, fraud_risk_percentage, audit_flagged, updated_at)
    SELECT 
        c.customer_id,
        COUNT(DISTINCT o.order_id) as o_cnt,
        COUNT(DISTINCT h.ticket_id) as r_cnt,
        CASE 
            WHEN COUNT(DISTINCT o.order_id) > 0 THEN 
                ROUND((COUNT(DISTINCT h.ticket_id)::numeric / COUNT(DISTINCT o.order_id)::numeric) * 100)
            ELSE 0
        END as risk,
        CASE 
            WHEN COUNT(DISTINCT o.order_id) > 0 AND (COUNT(DISTINCT h.ticket_id)::numeric / COUNT(DISTINCT o.order_id)::numeric) >= 0.3 THEN TRUE
            ELSE FALSE
        END as flag,
        CURRENT_TIMESTAMP
    FROM oltp.customers c
    LEFT JOIN oltp.orders o ON c.customer_id = o.customer_id
    LEFT JOIN oltp.hitl_queue h ON h.customer_id = c.customer_id AND h.type = 'refund_customer' AND h.status = 'approved'
    WHERE c.is_anonymized = FALSE
    GROUP BY c.customer_id
    ON CONFLICT (customer_id) DO UPDATE SET 
        order_count = EXCLUDED.order_count,
        refund_count = EXCLUDED.refund_count,
        fraud_risk_percentage = EXCLUDED.fraud_risk_percentage,
        audit_flagged = EXCLUDED.audit_flagged,
        updated_at = EXCLUDED.updated_at;

    -- 5. Sync ESG carbon facts from OLTP orders to OLAP warehouse
    INSERT INTO olap.dw_esg_carbon_facts (order_id, customer_id, bags_returned, co2_offset_grams, timestamp)
    SELECT 
        o.order_id,
        o.customer_id,
        o.bags_returned,
        o.bags_returned * 250 as co2_offset_grams,
        o.created_at
    FROM oltp.orders o
    WHERE o.bags_returned > 0
      AND o.order_id NOT IN (SELECT order_id FROM olap.dw_esg_carbon_facts);
END;
$$;
