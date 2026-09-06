package com.tradingplatform.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ContinuousPredictionSchedulerTest {

    private static final Instant MARKET = Instant.parse("2024-01-02T05:00:00Z");
    private static final Instant OVERNIGHT = Instant.parse("2024-01-02T20:00:00Z");

    @Test
    void runsInferenceOnlyDuringIndianMarketHours() {
        NiftyPredictionService predictions = mock(NiftyPredictionService.class);
        ContinuousPredictionScheduler scheduler = new ContinuousPredictionScheduler(
                predictions, true, 15, 0.2, Clock.fixed(MARKET, ZoneOffset.UTC));

        scheduler.scheduledInference();

        verify(predictions).predict(15, 0.2);
    }

    @Test
    void skipsInferenceOutsideMarketHours() {
        NiftyPredictionService predictions = mock(NiftyPredictionService.class);
        ContinuousPredictionScheduler scheduler = new ContinuousPredictionScheduler(
                predictions, true, 15, 0.2, Clock.fixed(OVERNIGHT, ZoneOffset.UTC));

        scheduler.scheduledInference();

        verifyNoInteractions(predictions);
    }

    @Test
    void classifiesSessionWindows() {
        assertEquals(PredictionScheduleWindow.MARKET, PredictionScheduleWindow.at(MARKET));
        assertEquals(PredictionScheduleWindow.OVERNIGHT, PredictionScheduleWindow.at(OVERNIGHT));
    }
}