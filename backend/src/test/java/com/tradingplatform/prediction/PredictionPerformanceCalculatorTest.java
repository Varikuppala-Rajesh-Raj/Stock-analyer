package com.tradingplatform.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingplatform.persistence.PredictionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PredictionPerformanceCalculatorTest {
    @Test
    void calculatesClassificationRegressionAndCalibrationMetricsFromEvaluatedRows() {
        PredictionEntity up = row("UP", "UP", "1", "1", "0", "0.5", "1");
        PredictionEntity down = row("DOWN", "UP", "0", "2", "0", "0.5", "2");
        PredictionEntity flat = row("FLAT", "FLAT", "0", "0", "0.5", "0", "3");

        PredictionPerformanceMetrics metrics = PredictionPerformanceCalculator.calculate(List.of(up, down, flat));

        assertEquals(3, metrics.sampleCount());
        assertEquals(2.0 / 3.0, metrics.accuracy(), 0.000001);
        assertEquals(2.0 / 3.0, metrics.mae(), 0.000001);
        assertEquals(Math.sqrt(4.0 / 3.0), metrics.rmse(), 0.000001);
        assertEquals(Instant.parse("2024-01-01T00:00:01Z"), metrics.evaluatedFrom());
    }

    private static PredictionEntity row(String actual, String predicted, String actualReturn,
                                        String predictedReturn, String upProbability,
                                        String downProbability, String second) {
        PredictionEntity row = new PredictionEntity();
        row.actualDirection = actual;
        row.prediction = predicted;
        row.actualReturnPercent = new BigDecimal(actualReturn);
        row.predictedReturnPercent = new BigDecimal(predictedReturn);
        row.upProbability = new BigDecimal(upProbability);
        row.downProbability = new BigDecimal(downProbability);
        row.sidewaysProbability = new BigDecimal("0.5");
        row.evaluatedAt = Instant.parse("2024-01-01T00:00:0" + second + "Z");
        return row;
    }
}