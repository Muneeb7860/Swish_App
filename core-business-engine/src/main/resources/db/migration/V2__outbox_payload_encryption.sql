-- V2__outbox_payload_encryption.sql
-- Alter transactional_outbox.payload to TYPE TEXT to support encrypted Base64 strings.
ALTER TABLE transactional_outbox ALTER COLUMN payload TYPE TEXT;
