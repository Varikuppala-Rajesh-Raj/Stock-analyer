ALTER TABLE predictions
    ADD COLUMN predicted_return_percent NUMERIC(12,6);

ALTER TABLE predictions
    ADD COLUMN predicted_range_low NUMERIC(20,6);

ALTER TABLE predictions
    ADD COLUMN predicted_range_high NUMERIC(20,6);

ALTER TABLE predictions
    ADD COLUMN feature_schema_version VARCHAR(100);

ALTER TABLE predictions
    ADD COLUMN context_timestamp TIMESTAMP WITH TIME ZONE;

ALTER TABLE predictions
    ADD COLUMN input_hash VARCHAR(128);