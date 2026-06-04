CREATE TABLE wholesale_orders (
    order_id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'INR',
    ai_status VARCHAR(50) DEFAULT 'PENDING',
    ai_reasoning TEXT,
    credit_limit_remaining NUMERIC(19, 2),
    placed_at TIMESTAMP NOT NULL,
    evaluated_at TIMESTAMP
);

CREATE INDEX idx_wholesale_orders_status ON wholesale_orders(ai_status);
