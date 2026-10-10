package com.tradingplatform.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "overnight_market_data")
public class OvernightMarketDataEntity {

    public enum Status {
        FRESH,
        STALE,
        DATA_NOT_AVAILABLE,
        PROVIDER_ERROR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "instrument_name", nullable = false, length = 40)
    public String instrumentName;

    @Column(name = "instrument_key", nullable = false, length = 160)
    public String instrumentKey;

    @Column(nullable = false, length = 24)
    public String provider;

    @Column(nullable = false, length = 24)
    public String segment;

    @Column(name = "last_price", precision = 20, scale = 6)
    public BigDecimal lastPrice;

    @Column(name = "open_price", precision = 20, scale = 6)
    public BigDecimal openPrice;

    @Column(name = "high_price", precision = 20, scale = 6)
    public BigDecimal highPrice;

    @Column(name = "low_price", precision = 20, scale = 6)
    public BigDecimal lowPrice;

    @Column(name = "close_price", precision = 20, scale = 6)
    public BigDecimal closePrice;

    @Column(name = "net_change", precision = 20, scale = 6)
    public BigDecimal netChange;

    @Column(precision = 24, scale = 6)
    public BigDecimal volume;

    @Column(name = "provider_timestamp")
    public Instant providerTimestamp;

    @Column(name = "session_id", length = 100)
    public String sessionId;

    @Column(name = "retrieved_at", nullable = false)
    public Instant retrievedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    public Status status;

    @Column(name = "error_message", length = 1000)
    public String errorMessage;
}
