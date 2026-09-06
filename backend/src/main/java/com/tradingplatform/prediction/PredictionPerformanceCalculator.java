package com.tradingplatform.prediction;

import com.tradingplatform.persistence.PredictionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Pure metrics calculator for evaluated predictions. */
public final class PredictionPerformanceCalculator {
    private static final List<String> LABELS = List.of("UP", "DOWN", "FLAT");

    private PredictionPerformanceCalculator() {
    }

    public static PredictionPerformanceMetrics calculate(List<PredictionEntity> predictions) {
        List<PredictionEntity> evaluated = predictions == null ? List.of() : predictions.stream()
                .filter(prediction -> prediction != null && prediction.evaluatedAt != null
                        && prediction.actualDirection != null && prediction.prediction != null)
                .toList();
        if (evaluated.isEmpty()) return new PredictionPerformanceMetrics(0, 0, 0, 0, 0, 0, 0, 0, null, null);

        int correct = 0;
        double absoluteError = 0;
        double squaredError = 0;
        int regressionCount = 0;
        double brier = 0;
        int brierCount = 0;
        int[][] confusion = new int[LABELS.size()][LABELS.size()];
        for (PredictionEntity prediction : evaluated) {
            String actual = normalize(prediction.actualDirection);
            String predicted = normalize(prediction.prediction);
            int actualIndex = LABELS.indexOf(actual);
            int predictedIndex = LABELS.indexOf(predicted);
            if (predicted.equals(actual)) correct++;
            if (actualIndex >= 0 && predictedIndex >= 0) confusion[actualIndex][predictedIndex]++;
            if (prediction.actualReturnPercent != null && prediction.predictedReturnPercent != null) {
                double error = prediction.actualReturnPercent.doubleValue() - prediction.predictedReturnPercent.doubleValue();
                absoluteError += Math.abs(error);
                squaredError += error * error;
                regressionCount++;
            }
            Double actualProbability = probability(prediction, actual);
            if (actualProbability != null) {
                brier += (actualProbability - 1) * (actualProbability - 1);
                for (String label : LABELS) if (!label.equals(actual)) {
                    Double probability = probability(prediction, label);
                    if (probability != null) brier += probability * probability;
                }
                brierCount++;
            }
        }

        double precision = 0, recall = 0, f1 = 0;
        for (int index = 0; index < LABELS.size(); index++) {
            int truePositive = confusion[index][index];
            int predictedTotal = 0, actualTotal = 0;
            for (int other = 0; other < LABELS.size(); other++) {
                predictedTotal += confusion[other][index];
                actualTotal += confusion[index][other];
            }
            double labelPrecision = predictedTotal == 0 ? 0 : (double) truePositive / predictedTotal;
            double labelRecall = actualTotal == 0 ? 0 : (double) truePositive / actualTotal;
            precision += labelPrecision;
            recall += labelRecall;
            f1 += labelPrecision + labelRecall == 0 ? 0 : 2 * labelPrecision * labelRecall / (labelPrecision + labelRecall);
        }
        Instant from = evaluated.stream().map(prediction -> prediction.evaluatedAt).min(Instant::compareTo).orElse(null);
        Instant to = evaluated.stream().map(prediction -> prediction.evaluatedAt).max(Instant::compareTo).orElse(null);
        return new PredictionPerformanceMetrics(evaluated.size(), (double) correct / evaluated.size(),
                precision / LABELS.size(), recall / LABELS.size(), f1 / LABELS.size(),
                regressionCount == 0 ? 0 : absoluteError / regressionCount,
                regressionCount == 0 ? 0 : Math.sqrt(squaredError / regressionCount),
                brierCount == 0 ? 0 : brier / brierCount, from, to);
    }

    private static Double probability(PredictionEntity prediction, String label) {
        BigDecimal value = switch (label) {
            case "UP" -> prediction.upProbability;
            case "DOWN" -> prediction.downProbability;
            case "FLAT" -> prediction.sidewaysProbability;
            default -> null;
        };
        return value == null ? null : value.doubleValue();
    }

    private static String normalize(String label) {
        return "NEUTRAL".equalsIgnoreCase(label) ? "FLAT" : label.toUpperCase();
    }
}