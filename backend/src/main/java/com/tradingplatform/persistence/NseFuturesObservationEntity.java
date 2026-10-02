package com.tradingplatform.persistence;

import com.tradingplatform.market.nse.NseFuturesObservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "nse_futures_observations")
public class NseFuturesObservationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "observation_timestamp", nullable = false)
    public Instant timestamp;

    @Column(name = "source_timestamp", nullable = false)
    public String sourceTimestamp;

    @Column(nullable = false)
    public String identifier;

    @Column(name = "instrument_type", nullable = false)
    public String instrumentType;

    @Column(nullable = false)
    public String expiry;

    @Column(name = "last_price", nullable = false, precision = 20, scale = 6)
    public BigDecimal lastPrice;

    @Column(name = "open_price", precision = 20, scale = 6)
    public BigDecimal openPrice;

    @Column(name = "high_price", precision = 20, scale = 6)
    public BigDecimal highPrice;

    @Column(name = "low_price", precision = 20, scale = 6)
    public BigDecimal lowPrice;

    @Column(name = "previous_close", precision = 20, scale = 6)
    public BigDecimal previousClose;

    @Column(name = "price_change", precision = 20, scale = 6)
    public BigDecimal priceChange;

    @Column(name = "percent_change", precision = 12, scale = 6)
    public BigDecimal percentChange;

    @Column(name = "open_interest", nullable = false)
    public Long openInterest;

    @Column(name = "change_in_open_interest")
    public Long changeInOpenInterest;

    @Column(name = "percent_change_in_open_interest", precision = 12, scale = 6)
    public BigDecimal percentChangeInOpenInterest;

    @Column(name = "cumulative_volume")
    public Long cumulativeVolume;

    @Column(precision = 24, scale = 6)
    public BigDecimal turnover;

    @Column(nullable = false)
    public String underlying;

    @Column(name = "underlying_value", nullable = false, precision = 20, scale = 6)
    public BigDecimal underlyingValue;

    @Column(name = "collected_at", nullable = false)
    public Instant collectedAt;

    public void update(NseFuturesObservation observation) {
        timestamp = observation.timestamp();
        sourceTimestamp = observation.sourceTimestamp();
        identifier = observation.identifier();
        instrumentType = observation.instrumentType();
        expiry = observation.expiry();
        lastPrice = decimal(observation.price());
        openPrice = decimal(observation.open());
        highPrice = decimal(observation.high());
        lowPrice = decimal(observation.low());
        previousClose = decimal(observation.previousClose());
        priceChange = decimal(observation.change());
        percentChange = decimal(observation.changePercent());
        openInterest = observation.openInterest();
        changeInOpenInterest = observation.changeInOpenInterest();
        percentChangeInOpenInterest = decimal(observation.percentChangeInOpenInterest());
        cumulativeVolume = observation.volume();
        turnover = decimal(observation.turnover());
        underlying = observation.underlying();
        underlyingValue = decimal(observation.underlyingValue());
        collectedAt = Instant.now();
    }

    public NseFuturesObservation toObservation() {
        return new NseFuturesObservation(timestamp, sourceTimestamp, identifier, instrumentType, expiry,
                value(lastPrice), value(openPrice), value(highPrice), value(lowPrice), value(previousClose),
                value(priceChange), value(percentChange), openInterest, changeInOpenInterest,
                value(percentChangeInOpenInterest), cumulativeVolume, value(turnover), underlying,
                value(underlyingValue));
    }

    private static BigDecimal decimal(Double value) {
        return value == null || !Double.isFinite(value) ? null : BigDecimal.valueOf(value);
    }

    private static double value(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }
}