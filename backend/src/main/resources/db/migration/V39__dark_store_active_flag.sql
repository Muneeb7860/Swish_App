-- V39__dark_store_active_flag.sql
-- Adds active column to dark_stores table to allow marking a store as inactive.

ALTER TABLE oltp.dark_stores ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
