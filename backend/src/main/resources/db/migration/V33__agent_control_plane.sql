-- V33__agent_control_plane.sql

-- 1. Drop the legacy audit table
DROP TABLE IF EXISTS oltp.agent_event_log CASCADE;

-- 2. Create Agent Registry
CREATE TABLE oltp.agent_registry (
  name VARCHAR(50) PRIMARY KEY,
  domain VARCHAR(50) NOT NULL,
  version VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('active', 'inactive', 'shadow')),
  owner_team VARCHAR(50) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 3. Create Agent Suggestion
CREATE TABLE oltp.agent_suggestion (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  trace_id UUID NOT NULL,
  agent_name VARCHAR(50) NOT NULL REFERENCES oltp.agent_registry(name) ON DELETE CASCADE,
  domain VARCHAR(50) NOT NULL,
  entity_id VARCHAR(100) NOT NULL,
  recommendation JSONB NOT NULL,
  confidence NUMERIC(3,2) NOT NULL,
  reason TEXT NOT NULL,
  impact VARCHAR(20) NOT NULL CHECK (impact IN ('low', 'medium', 'high')),
  status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected', 'executed', 'failed', 'expired')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() + INTERVAL '3 hours'),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Trigger to update updated_at on agent_suggestion
CREATE OR REPLACE FUNCTION oltp.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.updated_at = NOW();
   RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_agent_suggestion_updated_at 
BEFORE UPDATE ON oltp.agent_suggestion 
FOR EACH ROW EXECUTE FUNCTION oltp.update_updated_at_column();

-- 4. Create Policy Decision
CREATE TABLE oltp.policy_decision (
  id BIGSERIAL PRIMARY KEY,
  suggestion_id UUID NOT NULL REFERENCES oltp.agent_suggestion(id) ON DELETE CASCADE,
  decision VARCHAR(20) NOT NULL CHECK (decision IN ('approved', 'rejected', 'needs_human')),
  policy_version VARCHAR(30) NOT NULL,
  reason TEXT NOT NULL,
  decided_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 5. Create Execution Record
CREATE TABLE oltp.execution_record (
  id BIGSERIAL PRIMARY KEY,
  suggestion_id UUID NOT NULL REFERENCES oltp.agent_suggestion(id) ON DELETE CASCADE UNIQUE,
  decision_id BIGINT NOT NULL REFERENCES oltp.policy_decision(id) ON DELETE CASCADE,
  executed BOOLEAN NOT NULL,
  execution_result JSONB,
  error TEXT,
  executed_by VARCHAR(50) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 6. Create Outcome Record
CREATE TABLE oltp.outcome_record (
  suggestion_id UUID PRIMARY KEY REFERENCES oltp.agent_suggestion(id) ON DELETE CASCADE,
  measurement_window VARCHAR(100) NOT NULL,
  metrics JSONB NOT NULL,
  success BOOLEAN NOT NULL,
  evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  notes TEXT
);

-- Query and Foreign Key Indexing
CREATE INDEX idx_agent_suggestion_agent ON oltp.agent_suggestion (agent_name);
CREATE INDEX idx_agent_suggestion_status ON oltp.agent_suggestion (status);
CREATE INDEX idx_agent_suggestion_trace ON oltp.agent_suggestion (trace_id);
CREATE INDEX idx_policy_decision_suggestion ON oltp.policy_decision (suggestion_id);
CREATE INDEX idx_execution_record_suggestion ON oltp.execution_record (suggestion_id);
CREATE INDEX idx_execution_record_decision ON oltp.execution_record (decision_id);

-- Partial index for active HITL tasks
CREATE INDEX idx_agent_suggestion_pending 
ON oltp.agent_suggestion (created_at DESC) 
WHERE status IN ('pending');

-- Seed Registry with our active v0 agents
INSERT INTO oltp.agent_registry (name, domain, version, status, owner_team)
VALUES
  ('OpsAgent', 'inventory', '1.0.0', 'active', 'Operations'),
  ('RoutingAgent', 'routing', '1.0.0', 'active', 'Logistics'),
  ('PricingAgent', 'pricing', '1.0.0', 'active', 'Commercial'),
  ('RiskAgent', 'risk', '1.0.0', 'active', 'Security'),
  ('SupportAgent', 'support', '1.0.0', 'active', 'CustomerSuccess')
ON CONFLICT (name) DO UPDATE 
SET version = EXCLUDED.version, status = EXCLUDED.status, owner_team = EXCLUDED.owner_team;
