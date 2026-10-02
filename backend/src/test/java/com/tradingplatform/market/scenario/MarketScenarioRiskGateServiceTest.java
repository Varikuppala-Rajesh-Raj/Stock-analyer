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

    @Test
    void supportsConfiguredOneLotAutomationExposureAndRejectsLimitBreach() {
        var scenario = new MarketScenario("NIFTY", 22800, "UP", 75, 22875, 15, .75, "VALID");
        var gate = new MarketScenarioRiskGateService();

        var withinPolicy = gate.evaluate(scenario, .8, 2, 1, 20, true, 1, 20);
        var overPolicy = gate.evaluate(scenario, .8, 2, 1.01, 20.01, true, 1, 20);

        assertTrue(withinPolicy.allowed());
        assertTrue(!overPolicy.allowed());
        assertTrue(overPolicy.reasons().contains("MAX_LOSS_LIMIT_EXCEEDED"));
        assertTrue(overPolicy.reasons().contains("MAX_EXPOSURE_LIMIT_EXCEEDED"));
    }
}
