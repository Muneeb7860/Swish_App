-- Agent Event Log: full audit trail for all agent suggestions + policy decisions
CREATE TABLE IF NOT EXISTS oltp.agent_event_log (
    id              BIGSERIAL       PRIMARY KEY,
    event_type      VARCHAR(50)     NOT NULL DEFAULT 'agent_suggestion',
    agent           VARCHAR(50)     NOT NULL,
    domain          VARCHAR(50)     NOT NULL,
    input_summary   TEXT,
    output_json     TEXT            NOT NULL,
    policy_status   VARCHAR(30)     NOT NULL,
    policy_reason   TEXT,
    executed        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_event_log_domain   ON oltp.agent_event_log (domain);
CREATE INDEX idx_agent_event_log_status   ON oltp.agent_event_log (policy_status);
CREATE INDEX idx_agent_event_log_created  ON oltp.agent_event_log (created_at DESC);

-- Seed agent policy thresholds into system_configurations
INSERT INTO oltp.system_configurations (config_key, config_value)
VALUES
    ('pricing.auto_approve_pct', '5'),
    ('pricing.manager_approval_pct', '10'),
    ('pricing.hitl_pct', '15'),
    ('pricing.reject_above_pct', '15'),
    ('risk.ignore_below_confidence', '0.3'),
    ('risk.auto_approve_confidence', '0.8'),
    ('inventory.auto_approve_confidence', '0.6'),
    ('routing.hitl_impact', 'high'),
    ('routing.auto_approve_confidence', '0.65'),
    ('agent.suggestion_expiry_minutes', '180')
ON CONFLICT (config_key) DO NOTHING;
