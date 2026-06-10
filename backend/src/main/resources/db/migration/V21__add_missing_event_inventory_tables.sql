-- Phase 21: Back-fill tables that had JPA entities but no Flyway migration.
--
-- domain_events (event domain) and inventory_items (hexagonal inventory domain)
-- previously existed ONLY via Hibernate ddl-auto. A Flyway-managed production
-- boot (ddl-auto=validate) therefore failed with "missing table". Columns here
-- match the DomainEventEntity / InventoryItemEntity field mappings exactly so
-- schema validation passes. Both live in oltp, matching the schema= now declared
-- on their @Table annotations.

CREATE TABLE IF NOT EXISTS oltp.domain_events (
    event_id       VARCHAR(255) PRIMARY KEY,
    aggregate_id   VARCHAR(255),
    aggregate_type VARCHAR(255),
    event_type     VARCHAR(255),
    payload        TEXT,
    status         VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE,
    processed_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_domain_events_status ON oltp.domain_events (status);

CREATE TABLE IF NOT EXISTS oltp.inventory_items (
    id               VARCHAR(255) PRIMARY KEY,
    sku              VARCHAR(255) NOT NULL UNIQUE,
    available_amount INTEGER      NOT NULL,
    reserved_amount  INTEGER      NOT NULL,
    version          BIGINT
);
