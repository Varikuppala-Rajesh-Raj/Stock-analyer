package com.tradingplatform.market.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MarketScenarioBuilderServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void buildsValidUpScenarioWhenForecastIsConfident() throws Exception {
        var direction = json.readTree("""
            {
              "prediction": "UP",
              "probabilities": {"DOWN": 0.15, "NEUTRAL": 0.18, "UP": 0.67},
              "horizonMinutes": 30
            }
            """);

        var scenario = new MarketScenarioBuilderService().build(
                direction,
                22800.0,
                0.75,
                30,
                null
        );

        assertNotNull(scenario);
        assertEquals("UP", scenario.getDirection());
        assertEquals(171.0, scenario.getExpectedMovePoints(), 0.001);
        assertEquals(22971.0, scenario.getExpectedTarget(), 0.001);
        assertEquals(30, scenario.getHorizonMinutes());
        assertEquals("VALID", scenario.getScenarioValidity());
    }

    @Test
    void downgradesLowConfidenceForecastToLowConfidenceScenario() throws Exception {
        var direction = json.readTree("""
            {
              "prediction": "UP",
              "probabilities": {"DOWN": 0.35, "NEUTRAL": 0.33, "UP": 0.32},
              "horizonMinutes": 15
            }
            """);

        var scenario = new MarketScenarioBuilderService().build(
                direction,
                22800.0,
                0.32,
                15,
                null
        );

        assertEquals("LOW_CONFIDENCE", scenario.getScenarioValidity());
        assertEquals("UP", scenario.getDirection());
    }
}
