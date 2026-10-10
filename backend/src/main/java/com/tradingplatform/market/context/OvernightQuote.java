package com.tradingplatform.market.context;

import java.math.BigDecimal;
import java.time.Instant;

public record OvernightQuote(
        String provider,
        String instrumentKey,
        BigDecimal value,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePct,
        Instant timestamp,
        Status status,
        String error,
        Instant retrievedAt,
        MarketSessionLifecycle.Status lifecycleStatus,
        String sessionId,
        String requiredSessionId
) {
    public enum Status {
        FRESH,
        STALE,
        UNAVAILABLE
    }
}
