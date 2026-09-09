ALTER TABLE predictions
    ADD COLUMN predicted_return_percent NUMERIC(12,6),
    ADD COLUMN predicted_range_low NUMERIC(20,6),
    ADD COLUMN predicted_range_high NUMERIC(20,6),
    ADD COLUMN feature_schema_version VARCHAR(100),
    ADD COLUMN context_timestamp TIMESTAMPTZ,
    ADD COLUMN input_hash VARCHAR(128);