-- Phase 2: Inventory & Wholesaler (renumbered from V8 — V8 was already used by
-- V8__align_rider_check_constraints.sql which lives in the canonical history).
--
-- Tables created here are aligned with the @Entity classes in
-- ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.*

CREATE SCHEMA IF NOT EXISTS wholesaler;

-- Vendor master (also referenced from oltp.b2b_restock_orders) -----------------
CREATE TABLE IF NOT EXISTS oltp.wholesalers (
    wholesaler_id            VARCHAR(50)  PRIMARY KEY,
    name                     VARCHAR(100) NOT NULL,
    is_primary               BOOLEAN      NOT NULL DEFAULT TRUE,
    trust_score              INTEGER      NOT NULL DEFAULT 100,
    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    academy_discount_active  BOOLEAN      NOT NULL DEFAULT FALSE,
    base_invoice_amount      NUMERIC(10,2) NOT NULL DEFAULT 25.00,
    CONSTRAINT wholesalers_trust_score_chk CHECK (trust_score BETWEEN 0 AND 100),
    CONSTRAINT wholesalers_base_invoice_chk CHECK (base_invoice_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_wholesalers_active ON oltp.wholesalers (is_active);

-- Purchase orders + line items -------------------------------------------------
CREATE TABLE IF NOT EXISTS wholesaler.purchase_orders (
    po_id                      VARCHAR(50)  PRIMARY KEY,
    store_id                   VARCHAR(50)  NOT NULL,
    vendor_name                VARCHAR(100) NOT NULL,
    status                     VARCHAR(30)  NOT NULL,
    inbound_date               TIMESTAMP WITH TIME ZONE,
    grn_verification_file_url  TEXT,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT purchase_orders_status_chk
        CHECK (status IN ('DRAFT', 'SENT', 'PARTIALLY_RECEIVED', 'RECEIVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_store    ON wholesaler.purchase_orders (store_id);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status   ON wholesaler.purchase_orders (status);

CREATE TABLE IF NOT EXISTS wholesaler.purchase_order_items (
    item_id        VARCHAR(50) PRIMARY KEY,
    po_id          VARCHAR(50) NOT NULL REFERENCES wholesaler.purchase_orders(po_id) ON DELETE CASCADE,
    product_id     VARCHAR(50) NOT NULL,
    requested_qty  INTEGER     NOT NULL,
    received_qty   INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT purchase_order_items_qty_chk CHECK (requested_qty >= 0 AND received_qty >= 0)
);

CREATE INDEX IF NOT EXISTS idx_po_items_po ON wholesaler.purchase_order_items (po_id);

-- Wastage logs (wholesaler-scope copy) -----------------------------------------
-- oltp.wastage_logs already exists from V6 for the in-store wastage flow.
-- This wholesaler-scope table tracks vendor returns / supplier-side write-offs
-- and is what the WastageLogEntity (schema = "wholesaler") binds to.
CREATE TABLE IF NOT EXISTS wholesaler.wastage_logs (
    log_id      VARCHAR(50) PRIMARY KEY,
    store_id    VARCHAR(50) NOT NULL,
    product_id  VARCHAR(50) NOT NULL,
    batch_id    VARCHAR(50),
    qty_wasted  INTEGER     NOT NULL,
    reason      VARCHAR(50) NOT NULL,
    logged_by   VARCHAR(50) NOT NULL,
    logged_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT wholesaler_wastage_reason_chk
        CHECK (reason IN ('EXPIRED', 'DAMAGED_IN_STORE', 'MELTED_COLD_CHAIN', 'OTHER')),
    CONSTRAINT wholesaler_wastage_qty_chk CHECK (qty_wasted > 0)
);

CREATE INDEX IF NOT EXISTS idx_w_wastage_store    ON wholesaler.wastage_logs (store_id);
CREATE INDEX IF NOT EXISTS idx_w_wastage_product  ON wholesaler.wastage_logs (product_id);
