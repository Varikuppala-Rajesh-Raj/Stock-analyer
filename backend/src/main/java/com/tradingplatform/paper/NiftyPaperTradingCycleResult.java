package com.tradingplatform.paper;

import java.math.BigDecimal;
import java.time.Instant;

public record NiftyPaperTradingCycleResult(
        Instant timestamp,
        String status,
        String reason,
        String direction,
        String optionType,
        String expiry,
        BigDecimal strike,
        Integer signalStrength,
        PaperDtos.Order order
) {}
