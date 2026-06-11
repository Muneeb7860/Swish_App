-- Phase 25 (R3 / FR-03): TimescaleDB telemetry hypertable for sensor readings.
--
-- Requires a TimescaleDB-enabled PostgreSQL (CI uses the timescale/timescaledb
-- image; prod must run TimescaleDB). High-frequency device readings land in a
-- time-partitioned hypertable for efficient time-series analytics. The PK
-- includes the partition column (recorded_at) as TimescaleDB requires.
--
-- Tests run on H2 with Flyway disabled, so this migration is exercised only on
-- real TimescaleDB (CI cypress-e2e backend startup + prod).

CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE IF NOT EXISTS oltp.sensor_readings (
    reading_id     BIGSERIAL,
    sensor_id      VARCHAR(50)   NOT NULL,
    recorded_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metric_type    VARCHAR(20)   NOT NULL,
    reading_value  NUMERIC(12,4) NOT NULL,
    PRIMARY KEY (reading_id, recorded_at)
);

SELECT create_hypertable('oltp.sensor_readings', 'recorded_at', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_sensor_readings_sensor_time
    ON oltp.sensor_readings (sensor_id, recorded_at DESC);
