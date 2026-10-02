CREATE TABLE paper_accounts (
 id UUID PRIMARY KEY, initial_balance NUMERIC(20,6) NOT NULL, available_cash NUMERIC(20,6) NOT NULL,
 used_margin NUMERIC(20,6) NOT NULL DEFAULT 0, realized_pnl NUMERIC(20,6) NOT NULL DEFAULT 0,
 daily_realized_pnl NUMERIC(20,6) NOT NULL DEFAULT 0, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE TABLE paper_orders (
 id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES paper_accounts(id), instrument_key VARCHAR(120) NOT NULL,
 symbol VARCHAR(80) NOT NULL, side VARCHAR(8) NOT NULL, quantity INTEGER NOT NULL, requested_price NUMERIC(20,6),
 execution_price NUMERIC(20,6), order_type VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL, charges NUMERIC(20,6) NOT NULL DEFAULT 0,
 rejection_reason VARCHAR(255), created_at TIMESTAMP WITH TIME ZONE NOT NULL, filled_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_paper_orders_account_time ON paper_orders(account_id, created_at DESC);
CREATE TABLE paper_positions (
 id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES paper_accounts(id), instrument_key VARCHAR(120) NOT NULL,
 symbol VARCHAR(80) NOT NULL, exchange VARCHAR(24) NOT NULL, side VARCHAR(8) NOT NULL, quantity INTEGER NOT NULL,
 average_entry_price NUMERIC(20,6) NOT NULL, current_price NUMERIC(20,6) NOT NULL, unrealized_pnl NUMERIC(20,6) NOT NULL DEFAULT 0,
 realized_pnl NUMERIC(20,6) NOT NULL DEFAULT 0, stop_loss NUMERIC(20,6), target NUMERIC(20,6), status VARCHAR(16) NOT NULL,
 opened_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_paper_positions_open_instrument ON paper_positions(instrument_key, status);
