package com.tradingplatform.paper;

import java.math.BigDecimal;

public record PaperPerformanceMetrics(
        long totalTrades,
        long openTrades,
        long closedTrades,
        long winningTrades,
        long losingTrades,
        double winRate,
        BigDecimal totalGrossPnl,
        BigDecimal totalCharges,
        BigDecimal totalSlippage,
        BigDecimal totalNetPnl,
        BigDecimal averageWin,
        BigDecimal averageLoss,
        BigDecimal profitFactor,
        BigDecimal expectancy,
        BigDecimal maxDrawdown,
        double averageHoldingMinutes,
        long callTrades,
        long putTrades,
        long noTradeEvents
) {
}
