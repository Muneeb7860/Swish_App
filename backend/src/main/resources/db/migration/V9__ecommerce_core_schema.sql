-- Phases 9, 10, 11, 12: E-Commerce Core

CREATE TABLE IF NOT EXISTS product_listings (
    product_id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    base_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS customer_profiles (
    profile_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    marketing_opt_in BOOLEAN DEFAULT FALSE,
    default_currency VARCHAR(10) DEFAULT 'CHF'
);

CREATE TABLE IF NOT EXISTS saga_customer_orders (
    order_id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    saga_state VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS promotions (
    code VARCHAR(255) PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    value DECIMAL(10, 2) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
