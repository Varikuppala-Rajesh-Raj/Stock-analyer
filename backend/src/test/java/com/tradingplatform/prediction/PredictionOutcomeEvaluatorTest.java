package com.tradingplatform.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tradingplatform.persistence.PredictionEntity;
import com.tradingplatform.persistence.PredictionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PredictionOutcomeEvaluatorTest {
    private static final Instant CREATED = Instant.parse("2024-01-01T10:00:00Z");

    @Test
    void evaluatesDirectionAndReturnAfterTheHorizon() {
        PredictionEntity prediction = prediction("UP");
        PredictionOutcomeService service = new PredictionOutcomeService(mock(PredictionRepository.class));

        boolean evaluated = service.evaluateIfDue(prediction, new BigDecimal("101"), CREATED.plusSeconds(15 * 60L));

        assertEquals(true, evaluated);
        assertEquals("1.00000000", prediction.actualReturnPercent.toPlainString());
        assertEquals("UP", prediction.actualDirection);
        assertEquals(true, prediction.outcomeCorrect);
        assertEquals(CREATED.plusSeconds(15 * 60L), prediction.evaluatedAt);
    }

    @Test
    void doesNotEvaluateBeforeDueOrEvaluateTwice() {
        PredictionRepository repository = mock(PredictionRepository.class);
        PredictionOutcomeService service = new PredictionOutcomeService(repository);
        PredictionEntity prediction = prediction("DOWN");

        assertFalse(service.evaluateIfDue(prediction, new BigDecimal("99"), CREATED.plusSeconds(14 * 60L)));
        assertNull(prediction.evaluatedAt);
        assertEquals(true, service.evaluateIfDue(prediction, new BigDecimal("99"), CREATED.plusSeconds(15 * 60L)));
        assertFalse(service.evaluateIfDue(prediction, new BigDecimal("98"), CREATED.plusSeconds(16 * 60L)));
        verify(repository).save(prediction);
    }

    private static PredictionEntity prediction(String direction) {
        PredictionEntity prediction = new PredictionEntity();
        prediction.timestamp = CREATED;
        prediction.horizonMinutes = 15;
        prediction.niftyPrice = new BigDecimal("100");
        prediction.prediction = direction;
        return prediction;
    }
}