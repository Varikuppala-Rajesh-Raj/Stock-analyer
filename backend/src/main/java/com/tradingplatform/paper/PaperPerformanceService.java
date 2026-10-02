package com.tradingplatform.paper;

import com.tradingplatform.persistence.PaperTradeJournalEntity;
import com.tradingplatform.persistence.PaperTradeJournalRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PaperPerformanceService {

    private final PaperTradeJournalRepository tradeJournalRepository;

    public PaperPerformanceService(PaperTradeJournalRepository tradeJournalRepository) {
        this.tradeJournalRepository = tradeJournalRepository;
    }

    public PaperPerformanceMetrics getPerformance() {
        List<PaperTradeJournalEntity> entries = tradeJournalRepository.findByTradeTypeOrderByTimestampDesc("TRADE");
        long totalTrades = entries.size();
        long closedTrades = entries.stream().filter(e -> e.getStatus() == PaperPositionStatus.CLOSED).count();
        long openTrades = entries.stream().filter(e -> e.getStatus() == PaperPositionStatus.OPEN).count();

        long winningTrades = entries.stream().filter(e -> e.getNetPnl() != null && e.getNetPnl().compareTo(BigDecimal.ZERO) > 0).count();
        long losingTrades = entries.stream().filter(e -> e.getNetPnl() != null && e.getNetPnl().compareTo(BigDecimal.ZERO) < 0).count();

        BigDecimal totalGrossPnl = entries.stream().map(e -> e.getGrossPnl() == null ? BigDecimal.ZERO : e.getGrossPnl()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCharges = entries.stream().map(e -> e.getCharges() == null ? BigDecimal.ZERO : e.getCharges()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSlippage = entries.stream().map(e -> e.getSlippage() == null ? BigDecimal.ZERO : e.getSlippage()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNetPnl = entries.stream().map(e -> e.getNetPnl() == null ? BigDecimal.ZERO : e.getNetPnl()).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageWin = winningTrades > 0
                ? entries.stream().filter(e -> e.getNetPnl() != null && e.getNetPnl().compareTo(BigDecimal.ZERO) > 0).map(PaperTradeJournalEntity::getNetPnl).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(winningTrades), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal averageLoss = losingTrades > 0
                ? entries.stream().filter(e -> e.getNetPnl() != null && e.getNetPnl().compareTo(BigDecimal.ZERO) < 0).map(e -> e.getNetPnl().negate()).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(losingTrades), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal profitFactor = totalCharges.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : totalGrossPnl.abs().max(BigDecimal.ONE).divide(totalCharges.max(BigDecimal.ONE), 6, RoundingMode.HALF_UP);
        BigDecimal expectancy = totalTrades > 0 ? totalNetPnl.divide(BigDecimal.valueOf(totalTrades), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal maxDrawdown = entries.stream().map(e -> e.getNetPnl() == null ? BigDecimal.ZERO : e.getNetPnl()).reduce(BigDecimal.ZERO, (a, b) -> a.min(b));

        double winRate = totalTrades > 0 ? (winningTrades * 100.0) / totalTrades : 0.0;
        double averageHoldingMinutes = entries.stream()
                .filter(e -> e.getHoldingMinutes() != null)
                .mapToLong(e -> e.getHoldingMinutes() == null ? 0L : e.getHoldingMinutes())
                .average()
                .orElse(0.0);

        long callTrades = entries.stream().filter(e -> "CE".equalsIgnoreCase(e.getOptionType())).count();
        long putTrades = entries.stream().filter(e -> "PE".equalsIgnoreCase(e.getOptionType())).count();
        long noTradeEvents = tradeJournalRepository.findByTradeTypeOrderByTimestampDesc("NO_TRADE").size();

        return new PaperPerformanceMetrics(
                totalTrades,
                openTrades,
                closedTrades,
                winningTrades,
                losingTrades,
                winRate,
                totalGrossPnl,
                totalCharges,
                totalSlippage,
                totalNetPnl,
                averageWin,
                averageLoss,
                profitFactor,
                expectancy,
                maxDrawdown,
                averageHoldingMinutes,
                callTrades,
                putTrades,
                noTradeEvents
        );
    }
}
