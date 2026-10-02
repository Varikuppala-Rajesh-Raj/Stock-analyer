package com.tradingplatform.paper;

public record PaperTradingAutomationDecision(
        boolean allowed,
        String reason,
        String signalKey,
        boolean tradeSubmitted,
        PaperDtos.Order order
) {
}
