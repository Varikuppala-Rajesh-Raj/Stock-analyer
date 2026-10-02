package com.tradingplatform.market.scenario;

import java.util.List;

public record ScenarioTradeDecision(
        String status,
        boolean allowed,
        List<String> reasons,
        String recommendedSide,
        String recommendedSymbol
) {
    public static final String ALLOW = "ALLOW";
    public static final String REJECT = "REJECT";
}
