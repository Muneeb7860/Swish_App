-- V35__fraud_agent.sql

-- 1. Add version column for optimistic locking on order holds
ALTER TABLE oltp.orders ADD COLUMN version INT NOT NULL DEFAULT 0;

-- 2. Add updated_at column (missing from V1)
ALTER TABLE oltp.orders ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();

-- 3. Expand status CHECK to include 'held'
ALTER TABLE oltp.orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE oltp.orders ADD CONSTRAINT orders_status_check 
    CHECK (status IN ('pending', 'picking', 'picked', 'shipping', 'delivered', 'spoiled', 'cancelled', 'held'));

-- 4. Index for fraud queries: find held orders efficiently
CREATE INDEX idx_orders_status_held ON oltp.orders (status) WHERE status = 'held';

-- 5. Trigger to auto-update updated_at on any UPDATE
CREATE OR REPLACE FUNCTION oltp.update_orders_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER orders_set_updated_at
    BEFORE UPDATE ON oltp.orders
    FOR EACH ROW
    EXECUTE FUNCTION oltp.update_orders_updated_at_column();

-- 6. Chargebacks table for outcome measurement
CREATE TABLE oltp.chargebacks (
    chargeback_id BIGSERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES oltp.orders(order_id),
    amount NUMERIC(12,2) NOT NULL,
    reason VARCHAR(255),
    filed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(50) NOT NULL DEFAULT 'disputed' 
        CHECK (status IN ('disputed', 'won', 'lost'))
);

CREATE INDEX idx_chargebacks_order_id ON oltp.chargebacks(order_id);
CREATE INDEX idx_chargebacks_filed_at ON oltp.chargebacks(filed_at);

-- 7. Seed FraudAgent in agent_registry
INSERT INTO oltp.agent_registry (name, domain, version, status, owner_team)
VALUES ('FraudAgent', 'risk', '1.0.0', 'active', 'Risk & Compliance')
ON CONFLICT (name) DO UPDATE 
    SET domain = EXCLUDED.domain, version = EXCLUDED.version, status = EXCLUDED.status;
