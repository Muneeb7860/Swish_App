import os

migration_dir = "backend/src/main/resources/db/migration"
os.makedirs(migration_dir, exist_ok=True)

migrations = {
    f"{migration_dir}/V8__wholesaler_schema.sql": """-- Phase 2: Inventory & Wholesaler

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
""",
    f"{migration_dir}/V9__ecommerce_core_schema.sql": """-- Phases 9, 10, 11, 12: E-Commerce Core

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
""",
    f"{migration_dir}/V10__fleet_logistics_schema.sql": """-- Phases 4, 13, 14: Logistics, Fleet, Geospatial

CREATE TABLE IF NOT EXISTS active_shipments (
    shipment_id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    rider_id VARCHAR(255),
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS rider_shifts (
    shift_id VARCHAR(255) PRIMARY KEY,
    rider_id VARCHAR(255) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS delivery_zones (
    zone_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    geo_polygon_wkt TEXT NOT NULL,
    status VARCHAR(50) NOT NULL
);
""",
    f"{migration_dir}/V11__governance_support_schema.sql": """-- Phases 6, 7, 15: Telemetry, Agents, Support

CREATE TABLE IF NOT EXISTS audit_records (
    audit_id VARCHAR(255) PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    payload_snapshot TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_profiles (
    agent_id VARCHAR(255) PRIMARY KEY,
    capabilities VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS support_tickets (
    ticket_id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL
);
""",
    f"{migration_dir}/V12__notifications_feedback_schema.sql": """-- Phases 5, 8: Notification, Feedback

CREATE TABLE IF NOT EXISTS notifications (
    notification_id VARCHAR(255) PRIMARY KEY,
    recipient_id VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    message_body TEXT NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS customer_reviews (
    review_id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    rating INT NOT NULL,
    comment TEXT
);
"""
}

for path, content in migrations.items():
    with open(path, "w") as f:
        f.write(content)

print("DB Migrations generated successfully.")
