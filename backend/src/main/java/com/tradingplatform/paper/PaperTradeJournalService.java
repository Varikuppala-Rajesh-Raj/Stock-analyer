package com.tradingplatform.paper;

import com.tradingplatform.market.scenario.MarketScenario;
import com.tradingplatform.market.scenario.ScenarioPaperTradeRequest;
import com.tradingplatform.market.scenario.ScenarioTradeDecision;
import com.tradingplatform.persistence.PaperAccountEntity;
import com.tradingplatform.persistence.PaperAccountRepository;
import com.tradingplatform.persistence.PaperTradeJournalEntity;
import com.tradingplatform.persistence.PaperTradeJournalRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaperTradeJournalService {

    private final PaperTradeJournalRepository journalRepository;
    private final PaperAccountRepository accountRepository;

    public PaperTradeJournalService(
            PaperTradeJournalRepository journalRepository,
            PaperAccountRepository accountRepository
    ) {
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
    }

    public void recordTradeEntry(
            ScenarioPaperTradeRequest request,
            MarketScenario scenario,
            ScenarioTradeDecision decision,
            PaperDtos.Order order,
            int quantity,
            String optionSymbol
    ) {
        PaperAccountEntity account = accountRepository.findFirstByOrderByCreatedAtAsc().orElseGet(() -> {
            PaperAccountEntity entity = new PaperAccountEntity();
            entity.id = UUID.randomUUID();
            entity.initialBalance = BigDecimal.ZERO;
            entity.availableCash = BigDecimal.ZERO;
            entity.usedMargin = BigDecimal.ZERO;
            entity.realizedPnl = BigDecimal.ZERO;
            entity.dailyRealizedPnl = BigDecimal.ZERO;
            entity.dailyPnlDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
            entity.createdAt = Instant.now();
            entity.updatedAt = Instant.now();
            return accountRepository.save(entity);
        });

        BigDecimal price = order != null && order.executionPrice() != null ? order.executionPrice() : request.entryPrice();
        BigDecimal grossPnl = BigDecimal.ZERO;
        BigDecimal charges = order != null && order.charges() != null ? order.charges() : BigDecimal.ZERO;
        BigDecimal slippage = BigDecimal.ZERO;
        BigDecimal netPnl = BigDecimal.ZERO;

        PaperTradeJournalEntity entity = new PaperTradeJournalEntity(
                UUID.randomUUID(),
                account.id,
                order != null ? order.id() : UUID.randomUUID(),
                Instant.now(),
                "TRADE",
                request.symbol(),
                optionSymbol,
                request.optionType(),
                request.strike(),
                request.expiry(),
                scenario.getDirection(),
                BigDecimal.valueOf(scenario.getCurrentLevel()),
                BigDecimal.valueOf(scenario.getExpectedTarget()),
                BigDecimal.valueOf(scenario.getExpectedMovePoints()),
                BigDecimal.valueOf(scenario.getExpectedTarget()),
                scenario.getHorizonMinutes(),
                BigDecimal.valueOf(scenario.getConfidence()),
                scenario.getScenarioValidity(),
                price,
                price,
                quantity,
                request.lotSize(),
                grossPnl,
                charges,
                slippage,
                netPnl,
                Instant.now(),
                Instant.now(),
                0L,
                "ENTRY",
                decision.status(),
                String.join(",", decision.reasons()),
                "NIFTY_SCENARIO",
                "NORMAL",
                "NORMAL",
                "Paper order recorded for selected option.",
                PaperPositionStatus.OPEN
        );

        journalRepository.save(entity);
    }

    public void recordNoTrade(
            ScenarioPaperTradeRequest request,
            ScenarioTradeDecision decision,
            MarketScenario scenario,
            String reason
    ) {
        PaperAccountEntity account = accountRepository.findFirstByOrderByCreatedAtAsc().orElseGet(() -> {
            PaperAccountEntity entity = new PaperAccountEntity();
            entity.id = UUID.randomUUID();
            entity.initialBalance = BigDecimal.ZERO;
            entity.availableCash = BigDecimal.ZERO;
            entity.usedMargin = BigDecimal.ZERO;
            entity.realizedPnl = BigDecimal.ZERO;
            entity.dailyRealizedPnl = BigDecimal.ZERO;
            entity.dailyPnlDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
            entity.createdAt = Instant.now();
            entity.updatedAt = Instant.now();
            return accountRepository.save(entity);
        });

        PaperTradeJournalEntity entity = new PaperTradeJournalEntity(
                UUID.randomUUID(),
                account.id,
                UUID.randomUUID(),
                Instant.now(),
                "NO_TRADE",
                request.symbol(),
                request.optionSymbol(),
                request.optionType(),
                request.strike(),
                request.expiry(),
                scenario.getDirection(),
                BigDecimal.valueOf(scenario.getCurrentLevel()),
                BigDecimal.valueOf(scenario.getCurrentLevel()),
                BigDecimal.valueOf(scenario.getExpectedMovePoints()),
                BigDecimal.valueOf(scenario.getExpectedTarget()),
                scenario.getHorizonMinutes(),
                BigDecimal.valueOf(scenario.getConfidence()),
                scenario.getScenarioValidity(),
                request.entryPrice(),
                null,
                0,
                request.lotSize(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.now(),
                null,
                0L,
                "NO_TRADE",
                decision.status(),
                String.join(",", decision.reasons()),
                "NIFTY_SCENARIO",
                "NORMAL",
                "NORMAL",
                reason,
                PaperPositionStatus.REJECTED
        );

        journalRepository.save(entity);
    }

    public List<PaperTradeJournalEntity> listTrades() {
        return journalRepository.findByTradeTypeOrderByTimestampDesc("TRADE");
    }

    public List<PaperTradeJournalEntity> listNoTradeEvents() {
        return journalRepository.findByTradeTypeOrderByTimestampDesc("NO_TRADE");
    }
}
