-- ==========================================
-- SWISS QUICK COMMERCE NETWORK: PAYMENT ENGINE MIGRATION
-- ==========================================

CREATE TABLE oltp.payments (
    payment_id SERIAL PRIMARY KEY,
    order_id INT REFERENCES oltp.orders(order_id),
    customer_id VARCHAR(50) REFERENCES oltp.customers(customer_id),
    amount NUMERIC(10, 2) NOT NULL CHECK (amount >= 0.00),
    currency VARCHAR(3) NOT NULL DEFAULT 'CHF',
    payment_method VARCHAR(40) NOT NULL CHECK (payment_method IN ('Wallet', 'Swipe', 'PayPal', 'Paytm', 'Cash on Delivery', 'Direct Debit', 'Bank Transfer')),
    status VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED' CHECK (status IN ('AUTHORIZED', 'CAPTURED', 'FAILED', 'REFUNDED')),
    idempotency_key VARCHAR(100) UNIQUE,
    external_reference VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    captured_at TIMESTAMP WITH TIME ZONE,
    refunded_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_payments_customer_id ON oltp.payments(customer_id);
CREATE INDEX idx_payments_order_id ON oltp.payments(order_id);
