package com.tradingplatform.market.scenario;

import java.util.LinkedHashMap;
import java.util.Map;

public class MarketScenario {

    private final String symbol;
    private final double currentLevel;
    private final String direction;
    private final double expectedMovePoints;
    private final double expectedTarget;
    private final int horizonMinutes;
    private final double confidence;
    private final String scenarioValidity;

    public MarketScenario(
            String symbol,
            double currentLevel,
            String direction,
            double expectedMovePoints,
            double expectedTarget,
            int horizonMinutes,
            double confidence,
            String scenarioValidity
    ) {
        this.symbol = symbol;
        this.currentLevel = currentLevel;
        this.direction = direction;
        this.expectedMovePoints = expectedMovePoints;
        this.expectedTarget = expectedTarget;
        this.horizonMinutes = horizonMinutes;
        this.confidence = confidence;
        this.scenarioValidity = scenarioValidity;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getCurrentLevel() {
        return currentLevel;
    }

    public String getDirection() {
        return direction;
    }

    public double getExpectedMovePoints() {
        return expectedMovePoints;
    }

    public double getExpectedTarget() {
        return expectedTarget;
    }

    public int getHorizonMinutes() {
        return horizonMinutes;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getScenarioValidity() {
        return scenarioValidity;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("symbol", symbol);
        scenario.put("currentLevel", currentLevel);
        scenario.put("direction", direction);
        scenario.put("expectedMovePoints", expectedMovePoints);
        scenario.put("expectedTarget", expectedTarget);
        scenario.put("horizonMinutes", horizonMinutes);
        scenario.put("confidence", confidence);
        scenario.put("scenarioValidity", scenarioValidity);
        return scenario;
    }
}
