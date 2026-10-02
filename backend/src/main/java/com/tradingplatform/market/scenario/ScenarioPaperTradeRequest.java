package com.tradingplatform.market.scenario;

import java.math.BigDecimal;

public record ScenarioPaperTradeRequest(
        String symbol,
        String optionSymbol,
        String optionType,
        String strike,
        String expiry,
        int lotSize,
        double currentLevel,
        String direction,
        double expectedMovePoints,
        double expectedTarget,
        int horizonMinutes,
        double confidence,
        String scenarioValidity,
        double optionLiquidityScore,
        double riskRewardRatio,
        double maxLossPercent,
        double maxExposurePercent,
        boolean marketOpen,
        int quantity,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal targetPrice
) {
    public ScenarioPaperTradeRequest {
        if (optionSymbol == null || optionSymbol.isBlank()) {
            optionSymbol = symbol;
        }
        if (optionType == null || optionType.isBlank()) {
            optionType = "CE";
        }
        if (lotSize <= 0) {
            lotSize = 1;
        }
        if (quantity <= 0) {
            quantity = lotSize;
        }
    }

    public MarketScenario toScenario() {
        return new MarketScenario(
                symbol,
                currentLevel,
                direction,
                expectedMovePoints,
                expectedTarget,
                horizonMinutes,
                confidence,
                scenarioValidity
        );
    }
}
