package com.tradingplatform.market;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataNormalizerTest {

    @Test
    void normalizeCandles_removesDuplicateTimestampsAndSortsChronologically() {
        Instant t1 = Instant.parse("2024-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2024-01-01T09:00:00Z");
        Instant t3 = Instant.parse("2024-01-01T10:00:00Z");

        List<Candle> candles = List.of(
                new Candle(t1, new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("99"), new BigDecimal("100.5"), new BigDecimal("1000")),
                new Candle(t2, new BigDecimal("90"), new BigDecimal("95"), new BigDecimal("88"), new BigDecimal("92"), new BigDecimal("800")),
                new Candle(t3, new BigDecimal("100.5"), new BigDecimal("101.5"), new BigDecimal("99.5"), new BigDecimal("101"), new BigDecimal("1100"))
        );

        List<Candle> normalized = MarketDataNormalizer.normalizeCandles(candles);

        assertEquals(2, normalized.size());
        assertEquals(t2, normalized.get(0).timestamp());
        assertEquals(t1, normalized.get(1).timestamp());
    }

    @Test
    void isQuoteStale_detectsOldMarketData() {
        Quote quote = new Quote(
                "NIFTY",
                "NSE_INDEX|Nifty 50",
                new BigDecimal("24500"),
                new BigDecimal("24480"),
                new BigDecimal("24520"),
                new BigDecimal("24460"),
                new BigDecimal("24490"),
                new BigDecimal("10"),
                new BigDecimal("0.04"),
                new BigDecimal("1200000"),
                Instant.now().minus(Duration.ofMinutes(10))
        );

        assertTrue(MarketDataNormalizer.isQuoteStale(quote, Duration.ofMinutes(5)));
    }
}
