package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.market.Candle;
import com.tradingplatform.market.indicators.FeatureVector;
import com.tradingplatform.market.indicators.TechnicalFeatureService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketContextServiceTest {

    @Test
    void snapshotExcludesFutureCandlesAndComposesAllFeatureGroups() {
        TechnicalFeatureService technical = mock(TechnicalFeatureService.class);
        GlobalMarketContextService global = mock(GlobalMarketContextService.class);
        NewsContextService news = mock(NewsContextService.class);
        MarketContextService service = new MarketContextService(technical, global, news);
        Instant asOf = Instant.parse("2024-01-01T10:00:00Z");
        Candle current = candle(asOf, "100");
        Candle future = candle(asOf.plusSeconds(60), "200");
        FeatureVector vector = new FeatureVector(new BigDecimal("100"), null, null, null, null, null,
                null, null, null, null, null, null, new BigDecimal("10"), null, null, null, Map.of());
        when(technical.calculate(List.of(current))).thenReturn(vector);
        when(global.snapshot(asOf)).thenReturn(new GlobalMarketContext(asOf, 1.0, null, null, null, null, null, null, null, null, null, null, null, null));
        when(news.snapshot(asOf)).thenReturn(new NewsContext(asOf, 0.5, null, 1.0, null, null, null, null, null, null, null));

        MarketContext context = service.snapshot(asOf, List.of(future, current), Map.of("pcr", 1.2));

        verify(technical).calculate(eq(List.of(current)));
        assertEquals(100.0, context.technicalFeatures().get("close"));
        assertEquals(1.2, context.optionFeatures().get("pcr"));
        assertEquals(1.0, context.globalFeatures().get("gift_nifty_return_5m"));
        assertEquals(0.5, context.newsFeatures().get("sentiment_15m"));
    }

    private static Candle candle(Instant timestamp, String close) {
        BigDecimal value = new BigDecimal(close);
        return new Candle(timestamp, value, value, value, value, BigDecimal.ONE);
    }
}