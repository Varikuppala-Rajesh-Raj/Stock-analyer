CREATE TABLE paper_automation_cycles (
    id UUID PRIMARY KEY,
    cycle_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason TEXT NOT NULL,
    direction VARCHAR(16),
    option_type VARCHAR(8),
    expiry VARCHAR(16),
    strike NUMERIC(20,6),
    signal_strength INTEGER,
    paper_order_id UUID
);

CREATE INDEX idx_paper_automation_cycles_timestamp
    ON paper_automation_cycles(cycle_timestamp DESC);
