package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Numerical news/event context.
 *
 * The news provider should convert raw headlines into these
 * timestamped numerical features.
 */
public record NewsContext(

        Instant timestamp,

        Double sentiment15m,
        Double sentiment1h,

        Double newsCount15m,
        Double newsCount1h,

        Double indiaNewsSentiment,
        Double globalNewsSentiment,

        Double geopoliticalRisk,

        Double rbiEvent,
        Double fedEvent,

        Double macroEventRisk
) {

        public NewsContext(Double sentiment15m, Double sentiment1h,
                                           Double newsCount15m, Double newsCount1h,
                                           Double indiaNewsSentiment, Double globalNewsSentiment,
                                           Double geopoliticalRisk, Double rbiEvent, Double fedEvent,
                                           Double macroEventRisk) {
                this(Instant.now(), sentiment15m, sentiment1h, newsCount15m,
                                newsCount1h, indiaNewsSentiment, globalNewsSentiment,
                                geopoliticalRisk, rbiEvent, fedEvent, macroEventRisk);
        }

    public Map<String, Double> toFeatures() {

        Map<String, Double> features =
                new LinkedHashMap<>();

        put(features, "sentiment_15m", sentiment15m);
        put(features, "sentiment_1h", sentiment1h);

        put(features, "count_15m", newsCount15m);
        put(features, "count_1h", newsCount1h);

        put(
                features,
                "india_sentiment",
                indiaNewsSentiment
        );

        put(
                features,
                "global_sentiment",
                globalNewsSentiment
        );

        put(
                features,
                "geopolitical_risk",
                geopoliticalRisk
        );

        put(features, "rbi_event", rbiEvent);
        put(features, "fed_event", fedEvent);

        put(
                features,
                "macro_event_risk",
                macroEventRisk
        );

        return features;
    }

    private static void put(
            Map<String, Double> map,
            String key,
            Double value
    ) {
        if (value != null && Double.isFinite(value)) {
            map.put(key, value);
        }
    }
}