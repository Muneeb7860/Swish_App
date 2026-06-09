-- Domain 8 Hardening: performance indexes for security audit trail and outbox queries

-- Trust Ledger: composite index for actor lookups (supports SecurityTrustLedgerRepository
-- findByActorTypeAndActorIdOrderByTimestampDesc — the primary compliance query path)
CREATE INDEX IF NOT EXISTS idx_security_trust_ledger_actor
    ON oltp.security_trust_ledger (actor_type, actor_id, timestamp DESC);

-- Trust Ledger: covering index for recent global audit timeline scans
CREATE INDEX IF NOT EXISTS idx_security_trust_ledger_timestamp
    ON oltp.security_trust_ledger (timestamp DESC);

-- Outbox events: composite index for SecurityAnomalyAnalyzer's filtered polling query
-- (WHERE event_type = 'security.anomaly' AND status = 'PENDING')
-- Also used by SecurityController.getComplianceReport() countByEventTypeAndStatus
CREATE INDEX IF NOT EXISTS idx_outbox_events_type_status
    ON oltp.outbox_events (event_type, status);
