package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureEngineTest {

    @Test
    void flattenProducesVersionedDeterministicNamespacedFeatures() {
        Instant timestamp = Instant.parse("2024-01-01T10:00:00Z");
        Map<String, Double> technical = new LinkedHashMap<>();
        technical.put("zeta", 2.0);
        technical.put("alpha", 1.0);
        technical.put("invalid", Double.NaN);
        MarketContext context = new MarketContext(timestamp, technical,
                Map.of("pcr", 1.2),
                Map.of("sp500_return", 0.01, "gift_nifty_overnight_return", 0.015, "nifty_opening_gap", 0.012),
                Map.of("sentiment_1h", -0.2));

        UnifiedFeatureVector vector = FeatureEngine.flatten(context);

        assertEquals(UnifiedFeatureSchema.VERSION, vector.schemaVersion());
        assertEquals(timestamp, vector.timestamp());
        assertEquals(List.of("intraday_momentum_alpha", "intraday_momentum_zeta", "option_pcr",
                "overnight_return_gift_nifty_overnight_return", "opening_gap_nifty_opening_gap",
                "macro_context_sp500_return", "news_context_sentiment_1h"),
                vector.features().keySet().stream().toList());
        assertFalse(vector.features().containsKey("technical_invalid"));
    }
}