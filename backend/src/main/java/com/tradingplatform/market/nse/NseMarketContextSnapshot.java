package com.tradingplatform.market.nse;

import java.time.Instant;

public record NseMarketContextSnapshot(
        Instant timestamp,
        NiftyContext nifty,
        FuturesContext futures,
        Metric futuresLead
) {
    public enum Status {
        COLLECTING, AVAILABLE, STALE, UNAVAILABLE
    }

    public record Metric(Double value, Status status) {}

    public record NiftyContext(
            String timestamp,
            Double ltp,
            Double open,
            Double high,
            Double low,
            Double previousClose,
            Double change,
            Double changePercent,
            Double overnightReturn,
            Double openingGap,
            Metric return1m,
            Metric return5m,
            Metric return10m,
            Metric return15m,
            Status status
    ) {}

    public record FuturesContext(
            String timestamp,
            String identifier,
            String instrumentType,
            String expiry,
            Double ltp,
            Double open,
            Double high,
            Double low,
            Double previousClose,
            Double change,
            Double changePercent,
            Double overnightReturn,
            Double openingGap,
            Long openInterest,
            Long changeInOpenInterest,
            Double percentChangeInOpenInterest,
            Long volume,
            Double turnover,
            String underlying,
            Double underlyingValue,
            Double basis,
            Metric return5m,
            Metric return10m,
            Metric return15m,
            Status status
    ) {}
}