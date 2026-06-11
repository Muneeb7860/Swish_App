-- Phase 24 (R3 / FR-01 sensor provisioning, FR-03 prerequisite):
-- Device registry. A retailer provisions sensors for a hub; each device is
-- issued a key (SHA-256 hash stored) it uses to authenticate when emitting
-- telemetry. The telemetry stream itself (→ TimescaleDB) is later in R3.

CREATE TABLE IF NOT EXISTS oltp.sensors (
    sensor_id        VARCHAR(50)  PRIMARY KEY,
    retailer_id      VARCHAR(50)  REFERENCES oltp.retailers(retailer_id) ON DELETE CASCADE,
    store_id         VARCHAR(50)  REFERENCES oltp.dark_stores(store_id) ON DELETE SET NULL,
    sensor_type      VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PROVISIONED',
    device_key_hash  VARCHAR(64),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at     TIMESTAMP WITH TIME ZONE,
    last_seen_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT sensors_type_chk   CHECK (sensor_type IN ('TEMPERATURE','HUMIDITY','GPS','DOOR')),
    CONSTRAINT sensors_status_chk CHECK (status IN ('PROVISIONED','ACTIVE','DECOMMISSIONED'))
);

CREATE INDEX IF NOT EXISTS idx_sensors_retailer ON oltp.sensors (retailer_id);
CREATE INDEX IF NOT EXISTS idx_sensors_device_key_hash ON oltp.sensors (device_key_hash);
