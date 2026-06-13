-- Phase 8 (HITL Console): widen the oltp.hitl_queue.type CHECK constraint.
--
-- The original V1 constraint allowed only ('b2b_payment','refund_customer',
-- 'fraud_audit'), but MasterOrchestratorService already writes type
-- 'agent_escalation' (budget/low-confidence escalations) — which violates the
-- constraint on PostgreSQL (H2 dev was lenient and masked it). Phase 8 also adds
-- 'pricing_review' for surge>2.5 / discount>15% flags raised by the pricing agent.
--
-- Drop the original constraint by its conventional PostgreSQL name and re-add a
-- widened one. IF EXISTS keeps this idempotent and safe on H2 (where the inline
-- check is auto-named and simply isn't enforced for these inserts).
ALTER TABLE oltp.hitl_queue DROP CONSTRAINT IF EXISTS hitl_queue_type_check;

ALTER TABLE oltp.hitl_queue
    ADD CONSTRAINT hitl_queue_type_check
    CHECK (type IN ('b2b_payment', 'refund_customer', 'fraud_audit',
                    'agent_escalation', 'pricing_review'));
