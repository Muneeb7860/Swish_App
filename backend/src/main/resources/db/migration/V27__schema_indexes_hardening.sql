-- Phase 27: Database schema indexing hardening for performance and lock reduction.

-- 1. Indexing for parent-child relationship cascades to avoid sequential scans on delete:
CREATE INDEX IF NOT EXISTS idx_customer_addresses_customer_id
    ON oltp.customer_addresses (customer_id);

CREATE INDEX IF NOT EXISTS idx_customer_payment_cards_customer_id
    ON oltp.customer_payment_cards (customer_id);

CREATE INDEX IF NOT EXISTS idx_order_items_item_id
    ON oltp.order_items (item_id);

CREATE INDEX IF NOT EXISTS idx_rider_academy_certificates_rider_id
    ON oltp.rider_academy_certificates (rider_id);

CREATE INDEX IF NOT EXISTS idx_procurement_approvals_wholesaler_id
    ON oltp.procurement_approvals (wholesaler_id);

CREATE INDEX IF NOT EXISTS idx_procurement_approvals_restock_order_id
    ON oltp.procurement_approvals (restock_order_id);

CREATE INDEX IF NOT EXISTS idx_b2b_restock_orders_store_id
    ON oltp.b2b_restock_orders (store_id);

CREATE INDEX IF NOT EXISTS idx_b2b_restock_orders_wholesaler_id
    ON oltp.b2b_restock_orders (wholesaler_id);

CREATE INDEX IF NOT EXISTS idx_hitl_queue_customer_id
    ON oltp.hitl_queue (customer_id);

CREATE INDEX IF NOT EXISTS idx_hitl_queue_order_id
    ON oltp.hitl_queue (order_id);

CREATE INDEX IF NOT EXISTS idx_sensors_store_id
    ON oltp.sensors (store_id);

CREATE INDEX IF NOT EXISTS idx_pickers_active_store_id
    ON oltp.pickers (active_store_id);

-- 2. Composite indexes for high-frequency queries:
-- Optimizes findByCustomerCustomerIdOrderByCreatedAtDesc query:
CREATE INDEX IF NOT EXISTS idx_orders_customer_created_at
    ON oltp.orders (customer_id, created_at DESC);

-- Optimizes findByAccountTypeAndActorIdOrderByLineIdDesc query:
CREATE INDEX IF NOT EXISTS idx_ledger_lines_actor_line_id
    ON oltp.ledger_lines (account_type, actor_id, line_id DESC);
