CREATE TABLE paper_pnl_snapshots (
    id BIGSERIAL PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES paper_accounts(id),
    snapshot_time TIMESTAMP WITH TIME ZONE NOT NULL,
    realized_pnl NUMERIC(20,6) NOT NULL,
    unrealized_pnl NUMERIC(20,6) NOT NULL,
    total_pnl NUMERIC(20,6) NOT NULL,
    equity NUMERIC(20,6) NOT NULL
);

CREATE INDEX idx_paper_pnl_account_time
    ON paper_pnl_snapshots(account_id, snapshot_time DESC);