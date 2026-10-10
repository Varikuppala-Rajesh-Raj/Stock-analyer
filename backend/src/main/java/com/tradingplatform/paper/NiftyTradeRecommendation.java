package com.tradingplatform.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NiftyTradeRecommendation(
        Instant timestamp,
        String status,
        String reason,
        String direction,
        String optionType,
        String expiry,
        BigDecimal strike,
        String instrumentKey,
        BigDecimal spotPrice,
        BigDecimal optionPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal targetPrice,
        BigDecimal riskReward,
        Integer signalStrength,
        String technicalSignal,
        BigDecimal expectedNiftyMovePoints,
        Integer quantity,
        List<String> evidence
) {}
