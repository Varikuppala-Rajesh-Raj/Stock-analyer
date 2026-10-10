package com.tradingplatform.market.context;

import java.math.BigDecimal;
import java.time.Instant;

public record OvernightRetrieveResult(
        String instrument,
        String instrumentKey,
        String provider,
        String source,
        String status,
        Instant dataTimestamp,
        Instant retrievedAt,
        BigDecimal lastPrice,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal netChange,
        BigDecimal volume,
        String message,
        MarketSessionLifecycle.Status lifecycleStatus,
        String sessionId,
        String requiredSessionId
) {
}
