CREATE TABLE nse_reference_snapshots (
    id BIGSERIAL PRIMARY KEY,
    dataset_key VARCHAR(32) NOT NULL,
    provider_timestamp TIMESTAMP WITH TIME ZONE,
    provider_date DATE,
    retrieved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_json TEXT NOT NULL
);

CREATE INDEX idx_nse_reference_snapshots_dataset_retrieved
    ON nse_reference_snapshots(dataset_key, retrieved_at DESC);

CREATE INDEX idx_nse_reference_snapshots_provider_timestamp
    ON nse_reference_snapshots(provider_timestamp DESC);
