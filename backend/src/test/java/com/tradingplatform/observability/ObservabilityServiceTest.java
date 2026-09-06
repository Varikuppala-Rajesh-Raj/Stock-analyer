package com.tradingplatform.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ObservabilityServiceTest {
    @Test
    void recordsLowCardinalityOperationalEventsWithoutPayloads() {
        ObservabilityService observability = new ObservabilityService(new SimpleMeterRegistry());

        observability.record("ML_FAILURE", "prediction-service");
        observability.record("ML_FAILURE", "prediction-service");

        assertEquals(2.0, observability.count("ML_FAILURE", "prediction-service"));
        assertEquals(0.0, observability.count("stale_quote", "market-data"));
    }
}