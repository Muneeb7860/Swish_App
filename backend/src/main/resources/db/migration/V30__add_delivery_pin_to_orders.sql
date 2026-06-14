-- V30__add_delivery_pin_to_orders.sql
-- Adds the delivery_pin column to oltp.orders.
--
-- OrderEntity declares @Column(name = "delivery_pin", length = 4) and checkout()
-- populates a generated 4-digit PIN, but no prior migration created the column.
-- Local builds masked this via Hibernate auto-DDL; CI staging runs Flyway against
-- real Postgres, so GET /api/v1/orders failed with
-- "column oe1_0.delivery_pin does not exist" → HTTP 500.

ALTER TABLE oltp.orders
    ADD COLUMN IF NOT EXISTS delivery_pin VARCHAR(4);
