-- ======================================================
-- SWISS QUICK COMMERCE: REWARDS & GOVERNANCE SCHEMAS
-- ======================================================

-- 1. Customer Loyalty Audit Log (event-driven points history)
CREATE TABLE oltp.customer_loyalty (
    loyalty_id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL REFERENCES oltp.customers(customer_id) ON DELETE CASCADE,
    points_changed INT NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_loyalty_cust ON oltp.customer_loyalty(customer_id);

-- 2. Procurement Approvals for Human-in-the-Loop (HITL) overrides
CREATE TABLE oltp.procurement_approvals (
    id SERIAL PRIMARY KEY,
    restock_order_id INT REFERENCES oltp.b2b_restock_orders(restock_order_id) ON DELETE SET NULL,
    wholesaler_id VARCHAR(50) REFERENCES oltp.wholesalers(wholesaler_id) ON DELETE CASCADE,
    amount NUMERIC(10, 2) NOT NULL CHECK (amount >= 0.00),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    override_by VARCHAR(100),
    override_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_procurement_approvals_status ON oltp.procurement_approvals(status);
