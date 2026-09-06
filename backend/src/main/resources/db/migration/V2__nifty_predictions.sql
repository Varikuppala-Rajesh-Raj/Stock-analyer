CREATE TABLE predictions (
  id UUID PRIMARY KEY, symbol VARCHAR(24) NOT NULL, timestamp TIMESTAMPTZ NOT NULL,
  nifty_price NUMERIC(20,6), horizon_minutes INTEGER NOT NULL,
  up_probability NUMERIC(8,6), down_probability NUMERIC(8,6), sideways_probability NUMERIC(8,6),
  prediction VARCHAR(16), confidence VARCHAR(16), expected_move_points NUMERIC(20,6),
  decision VARCHAR(32) NOT NULL, reason VARCHAR(255), model_version VARCHAR(80) NOT NULL,
  feature_version VARCHAR(80), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_predictions_symbol_timestamp ON predictions(symbol, timestamp DESC);
