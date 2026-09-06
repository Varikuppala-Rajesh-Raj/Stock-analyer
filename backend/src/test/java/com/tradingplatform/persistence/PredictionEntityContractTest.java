package com.tradingplatform.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PredictionEntityContractTest {
    @Test
    void exposesReproducibleForecastFields() {
        PredictionEntity entity = new PredictionEntity();
        entity.timestamp = Instant.parse("2024-01-01T10:00:00Z");
        entity.contextTimestamp = entity.timestamp;
        entity.predictedReturnPercent = new BigDecimal("0.25");
        entity.featureSchemaVersion = "nifty-context-features-v1";
        entity.inputHash = "hash";

        assertEquals(entity.timestamp, entity.contextTimestamp);
        assertEquals("0.25", entity.predictedReturnPercent.toPlainString());
        assertEquals("nifty-context-features-v1", entity.featureSchemaVersion);
    }
}