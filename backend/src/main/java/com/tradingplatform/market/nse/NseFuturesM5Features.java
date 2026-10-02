package com.tradingplatform.market.nse;

import java.time.Instant;

public record NseFuturesM5Features(
        Instant predictionTimestamp,
        Instant futuresSnapshotTimestamp,
        String instrumentIdentifier,
        String expiry,
        double lastPrice,
        long contractsTradedOverFiveMinutes,
        long openInterest,
        long openInterestChangeOverFiveMinutes,
        Long sessionChangeInOpenInterest,
        double underlyingValue
) {}