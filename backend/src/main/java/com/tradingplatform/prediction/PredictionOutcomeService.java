package com.tradingplatform.prediction;

import com.tradingplatform.persistence.PredictionEntity;
import com.tradingplatform.persistence.PredictionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates due predictions once and persists their actual outcomes. */
@Service
public class PredictionOutcomeService {
    private final PredictionRepository repository;

    public PredictionOutcomeService(PredictionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean evaluateIfDue(PredictionEntity prediction, BigDecimal actualPrice, Instant now) {
        if (prediction == null || prediction.timestamp == null || prediction.evaluatedAt != null) return false;
        Instant evaluationTime = now == null ? Instant.now() : now;
        Instant dueAt = prediction.timestamp.plusSeconds(Math.max(0, prediction.horizonMinutes) * 60L);
        if (evaluationTime.isBefore(dueAt)) return false;
        PredictionOutcomeEvaluator.evaluate(prediction, actualPrice, evaluationTime);
        repository.save(prediction);
        return true;
    }
}