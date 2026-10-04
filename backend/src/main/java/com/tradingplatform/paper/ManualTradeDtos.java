package com.tradingplatform.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ManualTradeDtos {
    private ManualTradeDtos() {}

    public record TradeRequest(
            String instrumentType,
            String symbol,
            String instrumentKey,
            String expiry,
            String optionType,
            int quantity
    ) {}

    public record Trade(
            UUID id,
            String instrumentType,
            String instrumentKey,
            String symbol,
            String exchange,
            LocalDate expiry,
            String optionType,
            BigDecimal strike,
            int quantity,
            BigDecimal entryPrice,
            BigDecimal currentPrice,
            BigDecimal exitPrice,
            BigDecimal entryCharges,
            BigDecimal exitCharges,
            BigDecimal unrealizedPnl,
            BigDecimal realizedPnl,
            ManualTradeStatus status,
            Instant openedAt,
            Instant closedAt,
            Instant priceUpdatedAt
    ) {}

    public record Portfolio(
            List<Trade> trades,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal totalPnl,
            int openTrades,
            int closedTrades,
            int winningTrades,
            int losingTrades
    ) {}
}
