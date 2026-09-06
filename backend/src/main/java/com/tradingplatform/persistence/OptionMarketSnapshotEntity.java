package com.tradingplatform.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "option_market_snapshots", uniqueConstraints = @UniqueConstraint(name = "uq_option_snapshot_timestamp_contract", columnNames = {"snapshot_timestamp", "instrument_key"}))
public class OptionMarketSnapshotEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "snapshot_timestamp", nullable = false) public Instant timestamp;
    @Column(nullable = false) public String symbol;
    @Column(nullable = false) public LocalDate expiry;
    @Column(name = "strike_price", nullable = false) public BigDecimal strikePrice;
    @Column(name = "option_type", nullable = false) public String optionType;
    @Column(name = "instrument_key", nullable = false) public String instrumentKey;
    @Column(name = "underlying_spot", nullable = false) public BigDecimal underlyingSpot;
    @Column(nullable = false) public BigDecimal ltp;
    public BigDecimal bid, ask, volume, oi, iv, delta, gamma, theta, vega;
    @Column(name = "technical_features", columnDefinition = "TEXT") public String technicalFeatures;
}
