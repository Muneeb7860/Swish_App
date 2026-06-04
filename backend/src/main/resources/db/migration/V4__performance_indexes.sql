-- =========================================================================================
-- V4__performance_indexes.sql
-- Module: Backend
-- Purpose: Optimize database read operations via secondary indexing on high-traffic columns
-- =========================================================================================

-- 1. Indexing for orders query paths (by customer, rider, store, and status)
CREATE INDEX idx_orders_customer_id ON oltp.orders(customer_id);
CREATE INDEX idx_orders_rider_id ON oltp.orders(rider_id);
CREATE INDEX idx_orders_store_id ON oltp.orders(store_id);
CREATE INDEX idx_orders_status ON oltp.orders(status);

-- 2. Indexing for inventory lookup paths (by store and category)
CREATE INDEX idx_inventory_store_id ON oltp.inventory(store_id);
CREATE INDEX idx_inventory_category ON oltp.inventory(category);

-- 3. Indexing for ledger lines double-entry relationships
CREATE INDEX idx_ledger_lines_entry_id ON oltp.ledger_lines(entry_id);
CREATE INDEX idx_ledger_lines_actor ON oltp.ledger_lines(account_type, actor_id);
