CREATE TABLE nse_futures_observations (
    id BIGSERIAL PRIMARY KEY,
    observation_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    source_timestamp VARCHAR(40) NOT NULL,
    identifier VARCHAR(120) NOT NULL,
    instrument_type VARCHAR(24) NOT NULL,
    expiry VARCHAR(32) NOT NULL,
    last_price NUMERIC(20,6) NOT NULL,
    open_price NUMERIC(20,6),
    high_price NUMERIC(20,6),
    low_price NUMERIC(20,6),
    previous_close NUMERIC(20,6),
    price_change NUMERIC(20,6),
    percent_change NUMERIC(12,6),
    open_interest BIGINT NOT NULL,
    change_in_open_interest BIGINT,
    percent_change_in_open_interest NUMERIC(12,6),
    cumulative_volume BIGINT,
    turnover NUMERIC(24,6),
    underlying VARCHAR(32) NOT NULL,
    underlying_value NUMERIC(20,6) NOT NULL,
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_nse_future_observation UNIQUE(identifier, observation_timestamp)
);

CREATE INDEX idx_nse_future_observation_time
    ON nse_futures_observations(observation_timestamp DESC);

CREATE INDEX idx_nse_future_observation_contract_time
    ON nse_futures_observations(identifier, observation_timestamp DESC);