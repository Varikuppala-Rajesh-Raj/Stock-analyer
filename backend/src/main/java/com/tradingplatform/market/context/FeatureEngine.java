package com.tradingplatform.market.context;

import java.util.LinkedHashMap;
import java.util.Locale;
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
                .forEach(entry -> target.put(buildFeatureName(prefix, entry.getKey()), entry.getValue()));
    }

    private static String buildFeatureName(String prefix, String key) {
        String safeKey = key == null ? "" : key;
        switch (prefix) {
            case "technical":
                return "intraday_momentum_" + safeKey;
            case "option":
                return "option_" + safeKey;
            case "global":
                return featureFamilyForGlobalKey(safeKey) + "_" + safeKey;
            case "news":
                return "news_context_" + safeKey;
            default:
                return prefix + "_" + safeKey;
        }
    }

    private static String featureFamilyForGlobalKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalized.contains("overnight")) {
            return "overnight_return";
        }
        if (normalized.contains("opening") || normalized.contains("gap")) {
            return "opening_gap";
        }
        return "macro_context";
    }
}