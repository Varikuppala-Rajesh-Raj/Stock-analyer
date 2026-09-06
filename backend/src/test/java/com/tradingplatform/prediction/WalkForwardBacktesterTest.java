package com.tradingplatform.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WalkForwardBacktesterTest {
    private static final Instant FIRST = Instant.parse("2024-01-01T10:00:00Z");

    @Test
    void replaysPointsChronologicallyAndPredictorSeesOnlyCurrentFeatures() {
        List<Double> observed = new ArrayList<>();
        WalkForwardBacktester.BacktestPoint later = point(FIRST.plusSeconds(60), 2.0);
        WalkForwardBacktester.BacktestPoint earlier = point(FIRST, 1.0);

        List<WalkForwardBacktester.BacktestResult> results = WalkForwardBacktester.run(
                List.of(later, earlier), features -> {
                    observed.add(features.get("value"));
                    return features.get("value") == 1.0 ? "UP" : "DOWN";
                });

        assertEquals(List.of(1.0, 2.0), observed);
        assertEquals("UP", results.get(0).predictedDirection());
        assertEquals("DOWN", results.get(1).predictedDirection());
    }

    @Test
    void rejectsFutureFeaturesAndOutcomesAvailableAtPredictionTime() {
        WalkForwardBacktester.BacktestPoint futureFeature = new WalkForwardBacktester.BacktestPoint(
                FIRST, FIRST.plusSeconds(1), Map.of("value", 1.0), FIRST.plusSeconds(60), "UP", BigDecimal.ONE);
        WalkForwardBacktester.BacktestPoint sameTimeOutcome = new WalkForwardBacktester.BacktestPoint(
                FIRST, FIRST, Map.of("value", 1.0), FIRST, "UP", BigDecimal.ONE);

        assertThrows(IllegalArgumentException.class, () -> WalkForwardBacktester.run(List.of(futureFeature), values -> "UP"));
        assertThrows(IllegalArgumentException.class, () -> WalkForwardBacktester.run(List.of(sameTimeOutcome), values -> "UP"));
    }

    private static WalkForwardBacktester.BacktestPoint point(Instant timestamp, double value) {
        return new WalkForwardBacktester.BacktestPoint(timestamp, timestamp, Map.of("value", value),
                timestamp.plusSeconds(60), "UP", BigDecimal.ONE);
    }
}