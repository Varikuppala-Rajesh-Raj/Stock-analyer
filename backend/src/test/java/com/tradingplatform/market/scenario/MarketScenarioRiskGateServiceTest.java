package com.tradingplatform.market.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarketScenarioRiskGateServiceTest {

    @Test
    void allowsHighQualityScenarioWithSufficientRiskReward() {
        var scenario = new MarketScenario(
                "NIFTY",
                22800.0,
                "UP",
                75.0,
                22875.0,
                30,
                0.72,
                "VALID"
        );

        var decision = new MarketScenarioRiskGateService().evaluate(
                scenario,
                0.85,
                1.8,
                1.0,
                2.0,
                true
        );

        assertTrue(decision.allowed());
        assertEquals("ALLOW", decision.status());
    }

    @Test
    void rejectsLowConfidenceOrNeutralScenario() {
        var scenario = new MarketScenario(
                "NIFTY",
                22800.0,
                "NEUTRAL",
                10.0,
                22810.0,
                15,
                0.45,
                "LOW_CONFIDENCE"
        );

        var decision = new MarketScenarioRiskGateService().evaluate(
                scenario,
                0.8,
                1.2,
                1.0,
                2.0,
                true
        );

        assertTrue(!decision.allowed());
        assertEquals("REJECT", decision.status());
    }
}
