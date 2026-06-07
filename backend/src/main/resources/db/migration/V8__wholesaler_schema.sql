-- Phase 2: Inventory & Wholesaler

CREATE TABLE IF NOT EXISTS inventory_items (
    id VARCHAR(255) PRIMARY KEY,
    sku VARCHAR(255) NOT NULL UNIQUE,
    available_amount INT NOT NULL DEFAULT 0,
    reserved_amount INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wholesale_orders (
    order_id VARCHAR(255) PRIMARY KEY,
    supplier_id VARCHAR(255) NOT NULL,
    expected_delivery TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS wholesale_order_lines (
    line_id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL REFERENCES wholesale_orders(order_id),
    sku VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL
);
