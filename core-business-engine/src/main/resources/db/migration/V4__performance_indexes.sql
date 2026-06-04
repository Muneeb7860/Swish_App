-- =========================================================================================
-- V4__performance_indexes.sql
-- Module: Core Business Engine
-- Purpose: Optimize database read operations via secondary indexing on high-traffic columns
-- =========================================================================================

-- 1. Indexing for payments lookup paths
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_customer_id ON payments(customer_id);
CREATE INDEX idx_payments_status ON payments(status);

-- 2. Indexing for inventory transactions append-only delta ledger
CREATE INDEX idx_inventory_transactions_sku ON inventory_transactions(sku);
CREATE INDEX idx_inventory_transactions_ref ON inventory_transactions(reference_id);
CREATE INDEX idx_inventory_transactions_created ON inventory_transactions(created_at);
