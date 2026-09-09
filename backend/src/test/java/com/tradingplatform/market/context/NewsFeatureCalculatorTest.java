package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewsFeatureCalculatorTest {
    private static final Instant AS_OF = Instant.parse("2024-01-01T10:00:00Z");

    @Test
    void excludesNewsPublishedAfterPredictionTimestamp() {
        NewsContext context = NewsFeatureCalculator.calculate(AS_OF, List.of(
                item("2024-01-01T09:55:00Z", 0.8, "india", "rbi"),
                item("2024-01-01T10:00:00Z", 0.2, "global", "macro"),
                item("2024-01-01T10:05:00Z", -1.0, "global", "fed")));

        assertEquals(2.0, context.newsCount15m());
        assertEquals(0.5, context.sentiment15m(), 0.000001);
        assertEquals(1.0, context.rbiEvent());
        assertEquals(0.0, context.fedEvent());
        assertEquals(1.0, context.macroEventRisk());
        assertEquals(0.2, context.globalNewsSentiment(), 0.000001, "Future article must not affect global sentiment");
    }

    @Test
    void returnsUnavailableFeaturesWhenNoNewsIsAvailable() {
        NewsContext context = NewsFeatureCalculator.calculate(AS_OF, List.of());

        assertNull(context.sentiment1h());
        assertEquals(0.0, context.newsCount1h());
        assertEquals(0.0, context.fedEvent());
    }

    private static NewsItem item(String publication, double sentiment, String category, String eventType) {
        return new NewsItem("headline", "source", "https://example.test", Instant.parse(publication), AS_OF,
                List.of("NIFTY"), category, sentiment, 1.0, eventType);
    }
}
