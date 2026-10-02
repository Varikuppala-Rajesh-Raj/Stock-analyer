package com.tradingplatform.market.context;

import com.tradingplatform.market.Candle;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Pure calculations for timestamped global-market features. */
public final class GlobalMarketFeatureCalculator {

    private GlobalMarketFeatureCalculator() {
    }

    public static Double returnPercent(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 8, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static Double returnOver(List<Candle> candles, Duration lookback) {
        if (candles == null || candles.isEmpty() || lookback == null || lookback.isNegative()) {
            return null;
        }

        List<Candle> ordered = candles.stream()
                .filter(candle -> candle != null && candle.timestamp() != null)
            .sorted((left, right) -> left.timestamp().compareTo(right.timestamp()))
                .toList();
        if (ordered.size() < 2) {
            return null;
        }

        Candle latest = ordered.get(ordered.size() - 1);
        Instant cutoff = latest.timestamp().minus(lookback);
        Candle baseline = ordered.stream()
                .filter(candle -> !candle.timestamp().isAfter(cutoff))
                .reduce((first, second) -> second)
                .orElse(null);
        return baseline == null ? null : returnPercent(latest.close(), baseline.close());
    }
}