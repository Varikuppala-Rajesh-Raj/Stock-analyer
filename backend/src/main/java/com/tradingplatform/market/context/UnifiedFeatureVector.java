package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reproducible, timestamped feature vector for context-based ML requests. */
public record UnifiedFeatureVector(String schemaVersion, Instant timestamp,
                                   Map<String, Double> features) {
    public UnifiedFeatureVector {
        features = features == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(features));
    }
}