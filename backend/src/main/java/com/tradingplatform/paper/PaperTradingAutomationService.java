package com.tradingplatform.paper;

import com.tradingplatform.market.scenario.ScenarioExecutionResult;
import com.tradingplatform.market.scenario.ScenarioExecutionService;
import com.tradingplatform.market.scenario.ScenarioPaperTradeRequest;
import com.tradingplatform.persistence.PaperOrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaperTradingAutomationService {
    private final ScenarioExecutionService scenarioExecutionService;
    private final PaperOrderRepository orders;
    private final PaperAutomationControlService control;
    private final Duration idempotencyWindow;
    private final boolean enabled;
    private final double maxLossPercent;
    private final double maxExposurePercent;
    private final ConcurrentHashMap<String, Instant> lastTradeAt = new ConcurrentHashMap<>();

    public PaperTradingAutomationService(
            ScenarioExecutionService scenarioExecutionService,
            PaperOrderRepository orders,
            PaperAutomationControlService control,
            @Value("${trading.paper.automation.idempotency-minutes:5}") long idempotencyMinutes,
            @Value("${trading.paper.automation.enabled:false}") boolean enabled,
            @Value("${trading.paper.automation.max-loss-percent:1}") double maxLossPercent,
            @Value("${trading.paper.automation.max-exposure-percent:20}") double maxExposurePercent
    ) {
        this.scenarioExecutionService = scenarioExecutionService;
        this.orders = orders;
        this.control = control;
        this.idempotencyWindow = Duration.ofMinutes(idempotencyMinutes);
        this.enabled = enabled;
        this.maxLossPercent = maxLossPercent;
        this.maxExposurePercent = maxExposurePercent;
    }

    public synchronized PaperTradingAutomationDecision runCycle(ScenarioPaperTradeRequest request) {
        if (!isEnabled()) {
            return rejected("AUTOMATION_DISABLED", request);
        }
        if (request == null) {
            return rejected("REQUEST_MISSING", null);
        }
        if (!request.marketOpen()) {
            return rejected("MARKET_CLOSED", request);
        }
        if (request.entryPrice() == null || request.entryPrice().signum() <= 0) {
            return rejected("ENTRY_PRICE_MISSING", request);
        }
        if (request.optionSymbol() == null || request.optionSymbol().isBlank()) {
            return rejected("OPTION_SYMBOL_MISSING", request);
        }

        String key = dedupeKey(request);
        Instant now = Instant.now();
        Instant last = lastTradeAt.get(key);
        if (last != null && Duration.between(last, now).compareTo(idempotencyWindow) < 0) {
            return new PaperTradingAutomationDecision(false, "DUPLICATE_SIGNAL", key, false, null);
        }
        if (request.optionSymbol().contains("|")
                && orders.existsByInstrumentKeyAndCreatedAtAfterAndStatus(
                        request.optionSymbol(), now.minus(idempotencyWindow), PaperOrderStatus.FILLED)) {
            return new PaperTradingAutomationDecision(false, "DUPLICATE_SIGNAL", key, false, null);
        }

        ScenarioExecutionResult result = scenarioExecutionService.execute(
                request, maxLossPercent, maxExposurePercent);
        boolean filled = result != null && result.allowed() && result.order() != null
                && result.order().status() == PaperOrderStatus.FILLED;
        if (!filled) {
            String reason = result == null || result.decision() == null
                    ? "AUTO_REJECTED"
                    : String.join("|", result.decision().reasons());
            if (result != null && result.order() != null
                    && result.order().status() == PaperOrderStatus.REJECTED) {
                reason = result.order().rejectionReason() == null
                        ? "PAPER_ORDER_REJECTED"
                        : "PAPER_ORDER_REJECTED:" + result.order().rejectionReason();
            }
            return new PaperTradingAutomationDecision(false, reason, key, false,
                    result == null ? null : result.order());
        }

        lastTradeAt.put(key, now);
        return new PaperTradingAutomationDecision(true, "TRADE_SUBMITTED", key, true, result.order());
    }

    public Map<String, Object> status() {
        return Map.of(
                "enabled", isEnabled(),
                "configuredEnabled", enabled,
                "stopped", control.isStopped(),
                "idempotencyWindowMinutes", idempotencyWindow.toMinutes(),
                "maxLotsPerTrade", 1,
                "maxLossPercent", maxLossPercent,
                "maxExposurePercent", maxExposurePercent,
                "trackedSignals", lastTradeAt.size(),
                "modelGate", "NOT_REQUIRED_FOR_DETERMINISTIC_PAPER_TRADING"
        );
    }

    public boolean isEnabled() {
        return enabled && !control.isStopped();
    }

    public synchronized void stop() {
        control.stop();
    }

    public synchronized void resume() {
        if (!enabled) {
            throw new IllegalStateException("Set PAPER_AUTO_TRADING=true before resuming automation");
        }
        control.resume();
    }

    private PaperTradingAutomationDecision rejected(String reason, ScenarioPaperTradeRequest request) {
        return new PaperTradingAutomationDecision(false, reason,
                request == null ? "" : dedupeKey(request), false, null);
    }

    private String dedupeKey(ScenarioPaperTradeRequest request) {
        return request.symbol() + "|" + request.optionSymbol() + "|" + request.direction()
                + "|" + request.expiry() + "|" + request.strike();
    }
}
