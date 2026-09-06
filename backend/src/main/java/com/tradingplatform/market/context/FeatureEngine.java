package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministically flattens a point-in-time MarketContext for ML consumers. */
public final class FeatureEngine {

    private FeatureEngine() {
    }

    public static UnifiedFeatureVector flatten(MarketContext context) {
        if (context == null || context.timestamp() == null) {
            throw new IllegalArgumentException("Market context and timestamp are required.");
        }

        Map<String, Double> features = new LinkedHashMap<>();
        append(features, "technical", context.technicalFeatures());
        append(features, "option", context.optionFeatures());
        append(features, "global", context.globalFeatures());
        append(features, "news", context.newsFeatures());
        return new UnifiedFeatureVector(UnifiedFeatureSchema.VERSION, context.timestamp(), features);
    }

    private static void append(Map<String, Double> target, String prefix, Map<String, Double> source) {
        if (source == null) {
            return;
        }
        source.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .filter(entry -> Double.isFinite(entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> target.put(prefix + "_" + entry.getKey(), entry.getValue()));
    }
}