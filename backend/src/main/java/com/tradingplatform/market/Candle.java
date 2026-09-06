package com.tradingplatform.market;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(Instant timestamp, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
    public Candle {
        if (timestamp == null || open == null || high == null || low == null || close == null || volume == null || open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0 || close.signum() <= 0 || volume.signum() < 0 || high.compareTo(low) < 0 || high.compareTo(open) < 0 || high.compareTo(close) < 0 || low.compareTo(open) > 0 || low.compareTo(close) > 0) throw new IllegalArgumentException("Invalid OHLCV candle");
    }
}
