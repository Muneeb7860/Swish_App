-- Phases 4, 13, 14: Logistics, Fleet, Geospatial

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
