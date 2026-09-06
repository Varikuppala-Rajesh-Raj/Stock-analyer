CREATE TABLE instruments (
  instrument_key VARCHAR(120) PRIMARY KEY, exchange VARCHAR(24) NOT NULL, segment VARCHAR(32) NOT NULL,
  symbol VARCHAR(80) NOT NULL, company_name VARCHAR(255), isin VARCHAR(32), instrument_type VARCHAR(48),
  trading_symbol VARCHAR(120), active BOOLEAN NOT NULL DEFAULT true, last_updated TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_instruments_exchange_segment_symbol ON instruments(exchange, segment, symbol);
CREATE INDEX idx_instruments_symbol ON instruments(symbol);
CREATE INDEX idx_instruments_active ON instruments(active);
CREATE TABLE market_candles (
 id BIGSERIAL PRIMARY KEY, instrument_key VARCHAR(120) NOT NULL REFERENCES instruments(instrument_key), timeframe VARCHAR(8) NOT NULL,
 timestamp TIMESTAMPTZ NOT NULL, open NUMERIC(20,6) NOT NULL, high NUMERIC(20,6) NOT NULL, low NUMERIC(20,6) NOT NULL,
 close NUMERIC(20,6) NOT NULL, volume NUMERIC(24,4) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT uq_market_candle UNIQUE(instrument_key, timeframe, timestamp)
);
CREATE INDEX idx_market_candles_instrument_time ON market_candles(instrument_key, timeframe, timestamp DESC);
