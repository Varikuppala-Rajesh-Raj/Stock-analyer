ALTER TABLE predictions
    ADD COLUMN actual_price NUMERIC(20,6),
    ADD COLUMN actual_return_percent NUMERIC(12,6),
    ADD COLUMN actual_direction VARCHAR(16),
    ADD COLUMN outcome_correct BOOLEAN,
    ADD COLUMN evaluated_at TIMESTAMPTZ;

CREATE INDEX idx_predictions_outcome_due
    ON predictions(symbol, evaluated_at, timestamp);