package com.tradingplatform.prediction;

import com.tradingplatform.persistence.PredictionEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Calculates a prediction outcome from a price available after its horizon. */
public final class PredictionOutcomeEvaluator {
    private PredictionOutcomeEvaluator() {
    }

    public static void evaluate(PredictionEntity prediction, BigDecimal actualPrice, Instant evaluatedAt) {
        if (prediction == null || prediction.timestamp == null || prediction.niftyPrice == null
                || prediction.niftyPrice.signum() <= 0 || actualPrice == null || actualPrice.signum() <= 0) {
            throw new IllegalArgumentException("Prediction and positive actual/reference prices are required.");
        }
        BigDecimal actualReturn = actualPrice.subtract(prediction.niftyPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(prediction.niftyPrice, 8, RoundingMode.HALF_UP);
        String direction = actualReturn.signum() > 0 ? "UP" : actualReturn.signum() < 0 ? "DOWN" : "FLAT";
        prediction.actualPrice = actualPrice;
        prediction.actualReturnPercent = actualReturn;
        prediction.actualDirection = direction;
        prediction.outcomeCorrect = direction.equalsIgnoreCase(normalizePrediction(prediction.prediction));
        prediction.evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }

    private static String normalizePrediction(String prediction) {
        if (prediction == null) return "";
        return "NEUTRAL".equalsIgnoreCase(prediction) ? "FLAT" : prediction.toUpperCase();
    }
}