package com.tradingplatform.market;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central normalization utilities for the market-data layer.
 *
 * These rules keep the existing Upstox-based data pipeline stable while adding
 * the safeguards required for reliable continuous forecasting:
 * - sort candles chronologically
 * - deduplicate repeated timestamps
 * - detect stale quote data
 * - detect stale candle data
 */
public final class MarketDataNormalizer {

    private MarketDataNormalizer() {
    }

    public static List<Candle> normalizeCandles(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }

        Map<Instant, Candle> deduplicated = new LinkedHashMap<>();

        for (Candle candle : candles) {
            if (candle == null || candle.timestamp() == null) {
                continue;
            }
            deduplicated.put(candle.timestamp(), candle);
        }

        if (deduplicated.isEmpty()) {
            return List.of();
        }

        List<Candle> ordered = new ArrayList<>(deduplicated.values());
        ordered.sort(java.util.Comparator.comparing(Candle::timestamp));
        return List.copyOf(ordered);
    }

    public static boolean isQuoteStale(Quote quote, Duration maxAge) {
        if (quote == null || quote.timestamp() == null || maxAge == null || maxAge.isNegative()) {
            return true;
        }

        return Duration.between(quote.timestamp(), Instant.now()).compareTo(maxAge) > 0;
    }

    public static boolean isCandleStale(Candle candle, Duration maxAge) {
        if (candle == null || candle.timestamp() == null || maxAge == null || maxAge.isNegative()) {
            return true;
        }

        return Duration.between(candle.timestamp(), Instant.now()).compareTo(maxAge) > 0;
    }
}
