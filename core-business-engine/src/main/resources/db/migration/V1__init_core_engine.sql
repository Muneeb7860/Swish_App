-- =========================================================================================
-- V1__init_core_engine.sql
-- Module: Core Business Engine
-- Purpose: Initialize Inventory Ledgers, Payments, Outbox, and Atomic Integrity Constraints
-- =========================================================================================

-- 1. TRANSACTIONAL OUTBOX TABLE
-- Replaces Debezium. Read by Spring Integration JDBC Poller.
CREATE TABLE transactional_outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE -- Null until processed by the poller
);

CREATE INDEX idx_outbox_unprocessed ON transactional_outbox (created_at) WHERE processed_at IS NULL;

-- 2. PAYMENTS (State Machine Data)
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    order_id VARCHAR(100) NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. INVENTORY ITEMS (Source of Truth for Stock Levels)
CREATE TABLE inventory_items (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    stock_level INT NOT NULL DEFAULT 0
);

-- 4. INVENTORY TRANSACTIONS (Append-Only Delta Ledger)
CREATE TABLE inventory_transactions (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(100) NOT NULL REFERENCES inventory_items(sku),
    quantity_delta INT NOT NULL, -- e.g., -5 for purchase, +50 for restock
    reference_id VARCHAR(100) NOT NULL, -- order_id or restock_id
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================================================
-- THE POSTGRESQL ATOMIC TRIGGER (TOCTOU Defense)
-- =========================================================================================
-- This trigger eliminates the need for JPA `FOR UPDATE` locking.
-- When a delta is inserted into `inventory_transactions`, this trigger automatically
-- applies it to `inventory_items.stock_level`. 
-- If stock drops below 0, the database aborts the entire transaction natively.

CREATE OR REPLACE FUNCTION apply_inventory_delta()
RETURNS TRIGGER AS $$
DECLARE
    current_stock INT;
BEGIN
    -- 1. Lock the specific SKU row for the duration of this trigger
    SELECT stock_level INTO current_stock 
    FROM inventory_items 
    WHERE sku = NEW.sku 
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'SKU % not found in inventory_items.', NEW.sku;
    END IF;

    -- 2. Check bounds BEFORE applying
    IF (current_stock + NEW.quantity_delta) < 0 THEN
        RAISE EXCEPTION 'INSUFFICIENT_STOCK: Cannot apply % to SKU % (Current: %)', 
            NEW.quantity_delta, NEW.sku, current_stock;
    END IF;

    -- 3. Apply the delta safely
    UPDATE inventory_items 
    SET stock_level = stock_level + NEW.quantity_delta
    WHERE sku = NEW.sku;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_delta
AFTER INSERT ON inventory_transactions
FOR EACH ROW
EXECUTE FUNCTION apply_inventory_delta();
