package com.tradingplatform.prediction;

import java.time.Instant;

/** Evaluation metrics calculated only from predictions with completed outcomes. */
public record PredictionPerformanceMetrics(
        int sampleCount,
        double accuracy,
        double precision,
        double recall,
        double f1,
        double mae,
        double rmse,
        double brierScore,
        Instant evaluatedFrom,
        Instant evaluatedTo
) {
}