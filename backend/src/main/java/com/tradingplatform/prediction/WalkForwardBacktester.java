package com.tradingplatform.prediction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Replays prepared point-in-time features in chronological order without look-ahead. */
public final class WalkForwardBacktester {
    private WalkForwardBacktester() {
    }

    public static List<BacktestResult> run(List<BacktestPoint> points,
                                           Function<Map<String, Double>, String> predictor) {
        if (predictor == null) throw new IllegalArgumentException("Backtest predictor is required.");
        return points == null ? List.of() : points.stream()
                .sorted(Comparator.comparing(BacktestPoint::predictionTimestamp))
                .map(point -> evaluate(point, predictor))
                .toList();
    }

    private static BacktestResult evaluate(BacktestPoint point,
                                            Function<Map<String, Double>, String> predictor) {
        if (point.predictionTimestamp() == null || point.featureTimestamp() == null
                || point.featureTimestamp().isAfter(point.predictionTimestamp())) {
            throw new IllegalArgumentException("Features cannot be newer than the prediction timestamp.");
        }
        if (point.outcomeAvailableAt() == null || !point.outcomeAvailableAt().isAfter(point.predictionTimestamp())) {
            throw new IllegalArgumentException("Outcome must become available after the prediction timestamp.");
        }
        String predicted = predictor.apply(point.features());
        return new BacktestResult(point.predictionTimestamp(), predicted,
                point.actualDirection(), point.actualReturnPercent(), point.outcomeAvailableAt());
    }

    public record BacktestPoint(Instant predictionTimestamp, Instant featureTimestamp,
                                Map<String, Double> features, Instant outcomeAvailableAt,
                                String actualDirection, BigDecimal actualReturnPercent) {
        public BacktestPoint {
            features = features == null ? Map.of() : Map.copyOf(features);
        }
    }

    public record BacktestResult(Instant predictionTimestamp, String predictedDirection,
                                 String actualDirection, BigDecimal actualReturnPercent,
                                 Instant outcomeAvailableAt) {
    }
}