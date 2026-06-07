-- Phases 6, 7, 15: Telemetry, Agents, Support

CREATE TABLE IF NOT EXISTS audit_records (
    audit_id VARCHAR(255) PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    payload_snapshot TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_profiles (
    agent_id VARCHAR(255) PRIMARY KEY,
    capabilities VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS support_tickets (
    ticket_id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL
);
