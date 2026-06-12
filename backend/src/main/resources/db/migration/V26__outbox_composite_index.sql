-- Phase 26: Performance optimization index for outbox query scheduling
-- Optimizes: findByStatusOrderByCreatedAtAsc ("PENDING") query path.
CREATE INDEX IF NOT EXISTS idx_outbox_events_status_created_at
    ON oltp.outbox_events (status, created_at ASC);
