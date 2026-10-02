package com.tradingplatform.market.scenario;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MarketScenarioRiskGateService {

    public ScenarioTradeDecision evaluate(
            MarketScenario scenario,
            double optionLiquidityScore,
            double riskRewardRatio,
            double maxLossPercent,
            double maxExposurePercent,
            boolean marketOpen
    ) {
        return evaluate(scenario, optionLiquidityScore, riskRewardRatio, maxLossPercent,
                maxExposurePercent, marketOpen, 2.0, 2.0);
    }

    public ScenarioTradeDecision evaluate(
            MarketScenario scenario,
            double optionLiquidityScore,
            double riskRewardRatio,
            double maxLossPercent,
            double maxExposurePercent,
            boolean marketOpen,
            double maxAllowedLossPercent,
            double maxAllowedExposurePercent
    ) {
        List<String> reasons = new ArrayList<>();

        if (scenario == null) {
            return new ScenarioTradeDecision(
                    ScenarioTradeDecision.REJECT,
                    false,
                    List.of("SCENARIO_MISSING"),
                    "NONE",
                    "NONE"
            );
        }

        if (!marketOpen) {
            reasons.add("MARKET_CLOSED");
        }

        if ("INVALID".equals(scenario.getScenarioValidity())) {
            reasons.add("SCENARIO_INVALID");
        }

        if ("LOW_CONFIDENCE".equals(scenario.getScenarioValidity()) || scenario.getConfidence() < 0.55) {
            reasons.add("LOW_CONFIDENCE");
        }

        if ("NEUTRAL".equalsIgnoreCase(scenario.getDirection())) {
            reasons.add("NEUTRAL_DIRECTION");
        }

        if (optionLiquidityScore < 0.7) {
            reasons.add("LOW_LIQUIDITY");
        }

        if (riskRewardRatio < 1.5) {
            reasons.add("RISK_REWARD_TOO_LOW");
        }

        if (maxLossPercent > maxAllowedLossPercent) {
            reasons.add("MAX_LOSS_LIMIT_EXCEEDED");
        }

        if (maxExposurePercent > maxAllowedExposurePercent) {
            reasons.add("MAX_EXPOSURE_LIMIT_EXCEEDED");
        }

        String recommendedSide = switch (scenario.getDirection()) {
            case "UP" -> "BUY_CALL";
            case "DOWN" -> "BUY_PUT";
            default -> "NO_TRADE";
        };

        if (reasons.isEmpty()) {
            return new ScenarioTradeDecision(
                    ScenarioTradeDecision.ALLOW,
                    true,
                    List.of(),
                    recommendedSide,
                    scenario.getSymbol()
            );
        }

        return new ScenarioTradeDecision(
                ScenarioTradeDecision.REJECT,
                false,
                reasons,
                "NO_TRADE",
                scenario.getSymbol()
        );
    }
}
