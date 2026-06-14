-- V31__add_delivery_proof_fields_to_orders.sql
-- Adds the remaining delivery-proof / rejection columns to oltp.orders.
--
-- OrderEntity declares proof_of_delivery_photo_url, rejection_reason, and
-- rejection_photo_url (all String), but no migration created them. Local builds
-- hid this via Hibernate auto-DDL; CI staging runs Flyway against real Postgres,
-- so GET /api/v1/orders selected these columns and failed with
-- "column oe1_0.proof_of_delivery_photo_url does not exist" → HTTP 500.
-- Completes the orders schema alignment started in V30 (delivery_pin).

ALTER TABLE oltp.orders
    ADD COLUMN IF NOT EXISTS proof_of_delivery_photo_url TEXT,
    ADD COLUMN IF NOT EXISTS rejection_reason            TEXT,
    ADD COLUMN IF NOT EXISTS rejection_photo_url         TEXT;
