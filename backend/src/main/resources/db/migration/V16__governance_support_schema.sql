-- Phase 6/7/15: Telemetry, Agents, Support (renumbered from V11).
-- Audit records, agent profiles, support tickets.

CREATE TABLE IF NOT EXISTS oltp.audit_records (
    audit_id          VARCHAR(50)  PRIMARY KEY,
    aggregate_id      VARCHAR(50)  NOT NULL,
    action            VARCHAR(50)  NOT NULL,
    actor_id          VARCHAR(50)  NOT NULL,
    payload_snapshot  TEXT,
    recorded_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_records_aggregate ON oltp.audit_records (aggregate_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_records_actor     ON oltp.audit_records (actor_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS oltp.agent_profiles (
    agent_id      VARCHAR(50)  PRIMARY KEY,
    capabilities  VARCHAR(255) NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT agent_profiles_status_chk CHECK (status IN ('ACTIVE', 'IDLE', 'OFFLINE', 'TRAINING'))
);

CREATE TABLE IF NOT EXISTS oltp.support_tickets (
    ticket_id    VARCHAR(50)  PRIMARY KEY,
    customer_id  VARCHAR(50)  NOT NULL,
    order_id     VARCHAR(50)  NOT NULL,
    priority     VARCHAR(20)  NOT NULL,
    status       VARCHAR(30)  NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT support_tickets_priority_chk CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT support_tickets_status_chk   CHECK (status   IN ('OPEN', 'IN_PROGRESS', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_support_tickets_customer ON oltp.support_tickets (customer_id, status);
CREATE INDEX IF NOT EXISTS idx_support_tickets_order    ON oltp.support_tickets (order_id);
