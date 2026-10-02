CREATE TABLE option_market_snapshots (
  id BIGSERIAL PRIMARY KEY,
  snapshot_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
  symbol VARCHAR(32) NOT NULL,
  expiry DATE NOT NULL,
  strike_price NUMERIC(20,6) NOT NULL,
  option_type VARCHAR(4) NOT NULL,
  instrument_key VARCHAR(160) NOT NULL,
  underlying_spot NUMERIC(20,6) NOT NULL,
  ltp NUMERIC(20,6) NOT NULL,
  bid NUMERIC(20,6), ask NUMERIC(20,6), volume NUMERIC(24,4), oi NUMERIC(24,4),
  iv NUMERIC(20,8), delta NUMERIC(20,8), gamma NUMERIC(20,8), theta NUMERIC(20,8), vega NUMERIC(20,8),
  technical_features TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT uq_option_snapshot_timestamp_contract UNIQUE(snapshot_timestamp, instrument_key)
);
CREATE INDEX idx_option_snapshots_symbol_time ON option_market_snapshots(symbol, snapshot_timestamp DESC);
CREATE INDEX idx_option_snapshots_expiry_strike_type ON option_market_snapshots(expiry, strike_price, option_type);
CREATE INDEX idx_option_snapshots_instrument_time ON option_market_snapshots(instrument_key, snapshot_timestamp DESC);
