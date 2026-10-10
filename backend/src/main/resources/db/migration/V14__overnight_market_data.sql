CREATE TABLE overnight_market_data (
    id BIGSERIAL PRIMARY KEY,
    instrument_name VARCHAR(40) NOT NULL,
    instrument_key VARCHAR(160) NOT NULL,
    provider VARCHAR(24) NOT NULL,
    segment VARCHAR(24) NOT NULL,
    last_price NUMERIC(20, 6),
    open_price NUMERIC(20, 6),
    high_price NUMERIC(20, 6),
    low_price NUMERIC(20, 6),
    close_price NUMERIC(20, 6),
    net_change NUMERIC(20, 6),
    volume NUMERIC(24, 6),
    provider_timestamp TIMESTAMP WITH TIME ZONE,
    retrieved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(24) NOT NULL,
    error_message VARCHAR(1000)
);

CREATE INDEX idx_overnight_market_data_instrument_key
    ON overnight_market_data(instrument_key);

CREATE INDEX idx_overnight_market_data_provider_timestamp
    ON overnight_market_data(provider_timestamp DESC);

CREATE INDEX idx_overnight_market_data_retrieved_at
    ON overnight_market_data(retrieved_at DESC);
