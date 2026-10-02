package com.tradingplatform.market.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MarketScenarioBuilderService {

    public MarketScenario build(
            JsonNode directionResult,
            double currentLevel,
            double expectedReturnPercent,
            int horizonMinutes,
            String fallbackDirection
    ) {
        String normalizedDirection = normalizeDirection(
                directionResult != null
                        ? directionResult.path("prediction").asText(fallbackDirection)
                        : fallbackDirection
        );

        if (!Double.isFinite(currentLevel) || currentLevel <= 0) {
            return new MarketScenario(
                    "NIFTY",
                    currentLevel,
                    normalizedDirection,
                    0.0,
                    0.0,
                    horizonMinutes,
                    0.0,
                    "INVALID"
            );
        }

        if (directionResult == null || !Double.isFinite(expectedReturnPercent)) {
            return new MarketScenario(
                    "NIFTY",
                    currentLevel,
                    normalizedDirection,
                    0.0,
                    currentLevel,
                    horizonMinutes,
                    0.0,
                    "INVALID"
            );
        }

        double confidence = estimateConfidence(directionResult, normalizedDirection);
        double expectedMovePoints = currentLevel * (expectedReturnPercent / 100.0);
        double expectedTarget = currentLevel + expectedMovePoints;

        if (normalizedDirection.equals("UP")) {
            expectedTarget = currentLevel + Math.abs(expectedMovePoints);
        } else if (normalizedDirection.equals("DOWN")) {
            expectedTarget = currentLevel - Math.abs(expectedMovePoints);
        } else {
            expectedTarget = currentLevel;
        }

        String validity = confidence < 0.55 ? "LOW_CONFIDENCE" : "VALID";

        if (normalizedDirection.equals("NEUTRAL") && Math.abs(expectedReturnPercent) < 0.01) {
            validity = "INVALID";
        }

        return new MarketScenario(
                "NIFTY",
                currentLevel,
                normalizedDirection,
                expectedMovePoints,
                expectedTarget,
                horizonMinutes,
                confidence,
                validity
        );
    }

    private static double estimateConfidence(JsonNode directionResult, String normalizedDirection) {
        JsonNode probabilities = directionResult.path("probabilities");
        if (probabilities == null || probabilities.isMissingNode() || !probabilities.isObject()) {
            return 0.0;
        }

        JsonNode candidate = probabilities.path(normalizedDirection);
        if (candidate == null || candidate.isMissingNode() || !candidate.isNumber()) {
            return 0.0;
        }

        return candidate.doubleValue();
    }

    private static String normalizeDirection(String value) {
        if (value == null) {
            return "NEUTRAL";
        }

        String direction = value.trim().toUpperCase(Locale.ROOT);
        if (direction.equals("UP") || direction.equals("DOWN") || direction.equals("NEUTRAL")) {
            return direction;
        }

        return "NEUTRAL";
    }
}
