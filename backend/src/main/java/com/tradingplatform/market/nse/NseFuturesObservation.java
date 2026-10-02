package com.tradingplatform.market.nse;

import java.time.Instant;

public record NseFuturesObservation(
        Instant timestamp,
        String sourceTimestamp,
        String identifier,
        String instrumentType,
        String expiry,
        double price,
        Double open,
        Double high,
        Double low,
        Double previousClose,
        Double change,
        Double changePercent,
        Long openInterest,
        Long changeInOpenInterest,
        Double percentChangeInOpenInterest,
        Long volume,
        Double turnover,
        String underlying,
        double underlyingValue
) {}