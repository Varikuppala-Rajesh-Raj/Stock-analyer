package com.tradingplatform.market.scenario;

import com.tradingplatform.paper.PaperDtos;

public record ScenarioExecutionResult(
        MarketScenario scenario,
        ScenarioTradeDecision decision,
        PaperDtos.Order order
) {
    public boolean allowed() {
        return decision != null && decision.allowed();
    }
}
