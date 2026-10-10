package com.tradingplatform.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "nse_reference_snapshots")
public class NseReferenceSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "dataset_key", nullable = false, length = 32)
    public String datasetKey;

    @Column(name = "provider_timestamp")
    public Instant providerTimestamp;

    @Column(name = "provider_date")
    public LocalDate providerDate;

    @Column(name = "retrieved_at", nullable = false)
    public Instant retrievedAt;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    public String payloadJson;
}
