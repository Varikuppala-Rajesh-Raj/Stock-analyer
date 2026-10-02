package com.tradingplatform.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.market.scenario.ScenarioExecutionResult;
import com.tradingplatform.market.scenario.ScenarioExecutionService;
import com.tradingplatform.market.scenario.ScenarioPaperTradeRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaperTradingAutomationService {

    private final ScenarioExecutionService scenarioExecutionService;
    private final MlPredictionService mlPredictionService;
    private final Duration idempotencyWindow;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Instant> lastTradeAt = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public PaperTradingAutomationService(
            ScenarioExecutionService scenarioExecutionService,
            MlPredictionService mlPredictionService,
            @Value("${trading.paper.automation.idempotency-minutes:5}") long idempotencyMinutes,
            @Value("${trading.paper.automation.enabled:true}") boolean enabled
    ) {
        this(scenarioExecutionService, mlPredictionService, Duration.ofMinutes(idempotencyMinutes), enabled);
    }

    public PaperTradingAutomationService(
            ScenarioExecutionService scenarioExecutionService,
            MlPredictionService mlPredictionService,
            Duration idempotencyWindow,
            boolean enabled
    ) {
        this.scenarioExecutionService = scenarioExecutionService;
        this.mlPredictionService = mlPredictionService;
        this.idempotencyWindow = idempotencyWindow == null ? Duration.ofMinutes(5) : idempotencyWindow;
        this.enabled = enabled;
    }

    public PaperTradingAutomationDecision runCycle(ScenarioPaperTradeRequest request) {
        if (!enabled) {
            return new PaperTradingAutomationDecision(false, "AUTOMATION_DISABLED", "", false, null);
        }
        if (request == null) {
            return new PaperTradingAutomationDecision(false, "REQUEST_MISSING", "", false, null);
        }
        if (request.marketOpen() == false) {
            return new PaperTradingAutomationDecision(false, "MARKET_CLOSED", dedupeKey(request), false, null);
        }
        if (request.entryPrice() == null || request.entryPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return new PaperTradingAutomationDecision(false, "ENTRY_PRICE_MISSING", dedupeKey(request), false, null);
        }
        if (request.optionSymbol() == null || request.optionSymbol().isBlank()) {
            return new PaperTradingAutomationDecision(false, "OPTION_SYMBOL_MISSING", dedupeKey(request), false, null);
        }
        String modelGateFailure = modelGateFailure();
        if (modelGateFailure != null) {
            return new PaperTradingAutomationDecision(false, modelGateFailure, dedupeKey(request), false, null);
        }

        String key = dedupeKey(request);
        Instant now = Instant.now();
        Instant last = lastTradeAt.get(key);
        if (last != null && Duration.between(last, now).compareTo(idempotencyWindow) < 0) {
            return new PaperTradingAutomationDecision(false, "DUPLICATE_SIGNAL", key, false, null);
        }

        ScenarioExecutionResult result = scenarioExecutionService.execute(request);
        if (result == null || !result.allowed()) {
            String reason = result == null || result.decision() == null || result.decision().reasons().isEmpty()
                    ? "AUTO_REJECTED"
                    : String.join("|", result.decision().reasons());
            return new PaperTradingAutomationDecision(false, reason, key, false, null);
        }

        lastTradeAt.put(key, now);
        return new PaperTradingAutomationDecision(
                true,
                "TRADE_SUBMITTED",
                key,
                result.order() != null,
                result.order()
        );
    }

    @Scheduled(fixedDelayString = "${trading.paper.automation.interval-ms:60000}")
    public void scheduledCycle() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            // Safe placeholder: the actual automation loop in this project is driven by a validated
            // ScenarioPaperTradeRequest, which prevents duplicate trades and blocks invalid windows.
        } finally {
            running.set(false);
        }
    }

    public Map<String, Object> status() {
        return Map.of(
                "enabled", enabled,
                "running", running.get(),
                "idempotencyWindowMinutes", idempotencyWindow.toMinutes(),
                "trackedSignals", lastTradeAt.size(),
                "modelsEligibleForAutomation", modelGateFailure() == null
        );
    }

    private String modelGateFailure() {
        try {
            if (!validatedModel(mlPredictionService.modelStatus())) {
                return "DIRECTION_MODEL_NOT_VALIDATED";
            }
            if (!validatedModel(mlPredictionService.magnitudeModelStatus())) {
                return "MAGNITUDE_MODEL_NOT_VALIDATED";
            }
            if (!validatedModel(mlPredictionService.optionMagnitudeModelStatus())) {
                return "OPTION_MODEL_NOT_VALIDATED";
            }
            return null;
        } catch (RuntimeException exception) {
            return "ML_SERVICE_UNAVAILABLE";
        }
    }

    private boolean validatedModel(JsonNode model) {
        if (model == null
                || !model.path("eligibleForAutomation").asBoolean(false)
                || !"VALIDATED".equalsIgnoreCase(model.path("validationStatus").asText())
                || model.path("modelVersion").asText().isBlank()
                || !model.path("validationMethod").asText().toLowerCase().contains("chronological")
                || model.path("trainingRows").asInt(0) <= 0
                || model.path("validationRows").asInt(0) <= 0) {
            return false;
        }
        String status = model.path("status").asText();
        return "VALIDATED".equalsIgnoreCase(status) || "READY".equalsIgnoreCase(status);
    }

    private String dedupeKey(ScenarioPaperTradeRequest request) {
        return request.symbol() + "|" + request.optionSymbol() + "|" + request.direction() + "|" + request.expiry() + "|" + request.strike();
    }
}
