ALTER TABLE overnight_market_data
    ADD COLUMN session_id VARCHAR(100);

CREATE INDEX idx_overnight_market_data_session
    ON overnight_market_data(instrument_name, session_id, retrieved_at DESC);
