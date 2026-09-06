package com.tradingplatform.prediction;

import com.tradingplatform.persistence.PredictionEntity;
import com.tradingplatform.persistence.PredictionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Provides rolling performance windows from completed prediction outcomes. */
@Service
public class PredictionPerformanceService {
    private final PredictionRepository repository;

    public PredictionPerformanceService(PredictionRepository repository) {
        this.repository = repository;
    }

    public PredictionPerformanceMetrics metrics(String symbol, Instant asOf, Duration window) {
        Instant end = asOf == null ? Instant.now() : asOf;
        Instant start = window == null ? Instant.MIN : end.minus(window);
        List<PredictionEntity> predictions = repository.findBySymbolAndEvaluatedAtIsNotNullOrderByEvaluatedAtDesc(symbol);
        return PredictionPerformanceCalculator.calculate(predictions.stream()
                .filter(prediction -> !prediction.evaluatedAt.isBefore(start) && !prediction.evaluatedAt.isAfter(end))
                .toList());
    }
}