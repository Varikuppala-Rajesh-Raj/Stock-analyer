package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Complete market context available at a specific prediction timestamp.
 *
 * IMPORTANT:
 * Every feature inside this context must be available at or before
 * the timestamp. Never inject future information.
 */
public record MarketContext(
        Instant timestamp,

        Map<String, Double> technicalFeatures,
        Map<String, Double> optionFeatures,
        Map<String, Double> globalFeatures,
        Map<String, Double> newsFeatures
) {

    public MarketContext {
        technicalFeatures = safe(technicalFeatures);
        optionFeatures = safe(optionFeatures);
        globalFeatures = safe(globalFeatures);
        newsFeatures = safe(newsFeatures);
    }

    private static Map<String, Double> safe(Map<String, Double> value) {
        return value == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(value);
    }

    public Map<String, Double> allFeatures() {

        Map<String, Double> result =
                new java.util.LinkedHashMap<>();

        technicalFeatures.forEach(
                (key, value) -> result.put("intraday_momentum_" + key, value)
        );

        optionFeatures.forEach(
                (key, value) -> result.put("option_" + key, value)
        );

        globalFeatures.forEach(
                (key, value) -> result.put(globalFeatureFamily(key) + "_" + key, value)
        );

        newsFeatures.forEach(
                (key, value) -> result.put("news_context_" + key, value)
        );

        return Collections.unmodifiableMap(result);
    }

    private static String globalFeatureFamily(String key) {
        String normalized = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("overnight")) {
            return "overnight_return";
        }
        if (normalized.contains("opening") || normalized.contains("gap")) {
            return "opening_gap";
        }
        return "macro_context";
    }
}