package com.tradingplatform.market.scenario;

import com.tradingplatform.paper.PaperDtos;
import com.tradingplatform.paper.PaperTradingEngine;
import com.tradingplatform.paper.PaperTradeJournalService;
import org.springframework.stereotype.Service;

@Service
public class ScenarioExecutionService {

    private final PaperTradingEngine paperTradingEngine;
    private final MarketScenarioRiskGateService riskGateService;
    private final ScenarioPaperTradeService scenarioPaperTradeService;
    private final PaperTradeJournalService journalService;

    public ScenarioExecutionService(
            PaperTradingEngine paperTradingEngine,
            MarketScenarioRiskGateService riskGateService,
            ScenarioPaperTradeService scenarioPaperTradeService,
            PaperTradeJournalService journalService
    ) {
        this.paperTradingEngine = paperTradingEngine;
        this.riskGateService = riskGateService;
        this.scenarioPaperTradeService = scenarioPaperTradeService;
        this.journalService = journalService;
    }

    public ScenarioExecutionResult execute(ScenarioPaperTradeRequest request) {
        return execute(request, 2.0, 2.0);
    }

    public ScenarioExecutionResult execute(
            ScenarioPaperTradeRequest request,
            double maxAllowedLossPercent,
            double maxAllowedExposurePercent
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Scenario trade request is required.");
        }

        MarketScenario scenario = request.toScenario();
        ScenarioTradeDecision decision = riskGateService.evaluate(
                scenario,
                request.optionLiquidityScore(),
                request.riskRewardRatio(),
                request.maxLossPercent(),
                request.maxExposurePercent(),
                request.marketOpen(),
                maxAllowedLossPercent,
                maxAllowedExposurePercent
        );

        if (!decision.allowed()) {
            journalService.recordNoTrade(
                    request,
                    decision,
                    scenario,
                    "Risk gate rejected the scenario before execution."
            );
            return new ScenarioExecutionResult(scenario, decision, null);
        }

        String optionSymbol = request.optionSymbol() != null && !request.optionSymbol().isBlank()
                ? request.optionSymbol()
                : request.symbol();

        int quantity = request.quantity() > 0 ? request.quantity() : Math.max(1, request.lotSize());
        PaperDtos.OrderRequest orderRequest = scenarioPaperTradeService.buildOrderRequest(
                scenario,
                optionSymbol,
                quantity,
                request.entryPrice(),
                request.stopLoss(),
                request.targetPrice()
        );

        PaperDtos.Order order = paperTradingEngine.submit(orderRequest);
        if (order.status() == com.tradingplatform.paper.PaperOrderStatus.FILLED) {
            journalService.recordTradeEntry(
                    request,
                    scenario,
                    decision,
                    order,
                    quantity,
                    optionSymbol
            );
            return new ScenarioExecutionResult(scenario, decision, order);
        }

        ScenarioTradeDecision rejected = new ScenarioTradeDecision(
                ScenarioTradeDecision.REJECT,
                false,
                java.util.List.of(order.rejectionReason() == null
                        ? "PAPER_ORDER_REJECTED"
                        : "PAPER_ORDER_REJECTED:" + order.rejectionReason()),
                "NO_TRADE",
                scenario.getSymbol()
        );
        journalService.recordNoTrade(request, rejected, scenario,
                "Paper order was rejected by the paper execution and risk controls.");
        return new ScenarioExecutionResult(scenario, rejected, order);
    }
}
