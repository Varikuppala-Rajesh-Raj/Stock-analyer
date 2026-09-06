package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingplatform.market.Candle;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalMarketFeatureCalculatorTest {

    @Test
    void returnOverUsesOnlyCandlesAtOrBeforeTheLookbackCutoff() {
        Instant start = Instant.parse("2024-01-01T10:00:00Z");
        List<Candle> candles = List.of(
                candle(start, "100"),
                candle(start.plus(Duration.ofMinutes(5)), "101"),
                candle(start.plus(Duration.ofMinutes(10)), "102"),
                candle(start.plus(Duration.ofMinutes(15)), "110")
        );

        assertEquals(0.10, GlobalMarketFeatureCalculator.returnOver(candles, Duration.ofMinutes(15)), 0.000001);
    }

    @Test
    void returnPercentReturnsFractionalReturn() {
        assertEquals(0.05, GlobalMarketFeatureCalculator.returnPercent(new BigDecimal("105"), new BigDecimal("100")), 0.000001);
    }

    private static Candle candle(Instant timestamp, String close) {
        BigDecimal value = new BigDecimal(close);
        return new Candle(timestamp, value, value, value, value, BigDecimal.ONE);
    }
}