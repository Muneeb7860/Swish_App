-- V34__agent_baseline.sql

CREATE TABLE oltp.agent_baseline (
    sku VARCHAR(100) NOT NULL,
    date DATE NOT NULL,
    revenue_7d NUMERIC(12,2) NOT NULL,
    margin_pct NUMERIC(5,4) NOT NULL,
    order_count_7d INT NOT NULL,
    last_order_created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (sku, date)
);

CREATE INDEX idx_agent_baseline_sku_date ON oltp.agent_baseline (sku, date DESC);
CREATE INDEX idx_agent_baseline_updated ON oltp.agent_baseline (updated_at);
