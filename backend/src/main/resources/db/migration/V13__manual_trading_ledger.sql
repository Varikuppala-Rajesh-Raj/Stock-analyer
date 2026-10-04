CREATE TABLE manual_trades (
    id UUID PRIMARY KEY,
    instrument_type VARCHAR(12) NOT NULL,
    instrument_key VARCHAR(120) NOT NULL,
    symbol VARCHAR(100) NOT NULL,
    exchange VARCHAR(24) NOT NULL,
    expiry DATE,
    option_type VARCHAR(2),
    strike NUMERIC(20, 6),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    entry_price NUMERIC(20, 6) NOT NULL,
    current_price NUMERIC(20, 6) NOT NULL,
    exit_price NUMERIC(20, 6),
    entry_charges NUMERIC(20, 6) NOT NULL DEFAULT 0,
    exit_charges NUMERIC(20, 6) NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(20, 6) NOT NULL DEFAULT 0,
    realized_pnl NUMERIC(20, 6) NOT NULL DEFAULT 0,
    status VARCHAR(12) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    price_updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_manual_trades_opened_at ON manual_trades(opened_at DESC);
CREATE INDEX idx_manual_trades_status ON manual_trades(status);
