package com.tradingplatform.market.options;

import com.fasterxml.jackson.databind.JsonNode;

/** Ensures option opportunities are based on a complete NIFTY forecast. */
public final class OptionForecastGuard {
    private OptionForecastGuard() {
    }

    public static void requireUsable(JsonNode direction, double predictedReturnPercent) {
        if (direction == null || direction.path("prediction").asText("").isBlank()
                || !Double.isFinite(predictedReturnPercent)) {
            throw new IllegalStateException("A finite NIFTY direction and magnitude forecast are required before scanning options.");
        }
        JsonNode probabilities = direction.path("probabilities");
        if (!finite(probabilities.path("DOWN"))
                || !finite(probabilities.path("NEUTRAL"))
                || !finite(probabilities.path("UP"))) {
            throw new IllegalStateException("NIFTY direction probabilities are incomplete or non-finite.");
        }
    }

    private static boolean finite(JsonNode value) {
        return value.isNumber() && Double.isFinite(value.doubleValue());
    }
}