-- V36__routing_agent.sql

-- 1. Add fragile flag to inventory
ALTER TABLE oltp.inventory ADD COLUMN fragile BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE oltp.inventory ADD COLUMN reserved_qty INT NOT NULL DEFAULT 0;

-- 2. Add warehouse_id to orders - VARCHAR(50) to match dark_stores
ALTER TABLE oltp.orders ADD COLUMN warehouse_id VARCHAR(50);
ALTER TABLE oltp.orders ADD COLUMN estimated_shipping_cost NUMERIC(10,2);
ALTER TABLE oltp.orders ADD CONSTRAINT fk_orders_warehouse 
  FOREIGN KEY (warehouse_id) REFERENCES oltp.dark_stores(store_id);

-- 3. Warehouse baseline table
CREATE TABLE oltp.warehouse_baseline (
  zip_prefix VARCHAR(5) NOT NULL,
  warehouse_id VARCHAR(50) NOT NULL REFERENCES oltp.dark_stores(store_id),
  avg_shipping_cost NUMERIC(10,2) NOT NULL,
  sample_size INT NOT NULL DEFAULT 0, -- track confidence
  last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (zip_prefix, warehouse_id)
);
CREATE INDEX idx_warehouse_baseline_zip ON oltp.warehouse_baseline(zip_prefix);

-- 4. Region preference fallback
CREATE TABLE oltp.region_pref (
  zip_prefix VARCHAR(5) PRIMARY KEY,
  primary_warehouse_id VARCHAR(50) NOT NULL REFERENCES oltp.dark_stores(store_id),
  secondary_warehouse_id VARCHAR(50) REFERENCES oltp.dark_stores(store_id)
);

-- 5. Shipments table for actual cost tracking
CREATE TABLE oltp.shipments (
  shipment_id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES oltp.orders(order_id),
  warehouse_id VARCHAR(50) NOT NULL REFERENCES oltp.dark_stores(store_id),
  carrier VARCHAR(50),
  estimated_shipping_cost NUMERIC(10,2),
  actual_shipping_cost NUMERIC(10,2), -- null until delivered
  status VARCHAR(20) NOT NULL DEFAULT 'pending' 
    CHECK (status IN ('pending', 'shipped', 'delivered', 'lost')),
  shipped_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_shipments_order_id ON oltp.shipments(order_id);
CREATE INDEX idx_shipments_delivered ON oltp.shipments(delivered_at) WHERE delivered_at IS NOT NULL;

-- 6. Register RoutingAgent
INSERT INTO oltp.agent_registry (name, domain, version, status, owner_team)
VALUES ('RoutingAgent', 'routing', '1.0.0', 'active', 'Logistics & Ops')
ON CONFLICT (name) DO UPDATE 
  SET domain=EXCLUDED.domain, version=EXCLUDED.version, status=EXCLUDED.status;
