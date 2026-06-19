-- V37__warehouse_capacity.sql

ALTER TABLE oltp.dark_stores ADD COLUMN daily_order_capacity INT NOT NULL DEFAULT 500;
