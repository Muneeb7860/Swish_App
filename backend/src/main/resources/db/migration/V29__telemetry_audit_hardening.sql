-- V29__telemetry_audit_hardening.sql
-- Hardening of telemetry audit compliance:
-- 1. Cryptographic hashing columns for sensor readings
-- 2. Calibration columns for sensors

ALTER TABLE oltp.sensor_readings
    ADD COLUMN IF NOT EXISTS previous_reading_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reading_hash VARCHAR(64);

ALTER TABLE oltp.sensors
    ADD COLUMN IF NOT EXISTS last_calibrated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS calibration_status VARCHAR(20) DEFAULT 'PENDING',
    ADD CONSTRAINT sensors_calibration_status_chk CHECK (calibration_status IN ('PENDING', 'CALIBRATED', 'FAILED'));
