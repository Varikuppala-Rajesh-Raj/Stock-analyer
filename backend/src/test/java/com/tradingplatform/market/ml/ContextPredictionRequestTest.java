package com.tradingplatform.market.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.context.UnifiedFeatureSchema;
import com.tradingplatform.market.context.UnifiedFeatureVector;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextPredictionRequestTest {

    @Test
    void serializesVersionedContextRequestWithoutChangingLegacyShape() throws Exception {
        UnifiedFeatureVector vector = new UnifiedFeatureVector(
                UnifiedFeatureSchema.VERSION,
                Instant.parse("2024-01-01T10:00:00Z"),
                Map.of("global_sp500_return", 0.01));
        MlPredictionService.ContextPredictionRequest request = new MlPredictionService.ContextPredictionRequest(
                "NIFTY", 15, 0.2, vector.timestamp(), vector.schemaVersion(), vector.features());

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(request);

        assertTrue(json.contains("\"context_features\""));
        assertTrue(json.contains("\"feature_schema_version\":\"" + UnifiedFeatureSchema.VERSION + "\""));
        assertEquals(0.01, mapper.readTree(json).path("context_features").path("global_sp500_return").doubleValue(), 0.000001);
    }
}