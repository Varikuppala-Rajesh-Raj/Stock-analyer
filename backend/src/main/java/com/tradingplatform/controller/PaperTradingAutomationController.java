package com.tradingplatform.controller;

import com.tradingplatform.market.scenario.ScenarioPaperTradeRequest;
import com.tradingplatform.paper.PaperTradingAutomationDecision;
import com.tradingplatform.paper.PaperTradingAutomationService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paper/automation")
public class PaperTradingAutomationController {

    private final PaperTradingAutomationService automationService;

    public PaperTradingAutomationController(PaperTradingAutomationService automationService) {
        this.automationService = automationService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return automationService.status();
    }

    @PostMapping("/run")
    public PaperTradingAutomationDecision run(@RequestBody ScenarioPaperTradeRequest request) {
        return automationService.runCycle(request);
    }
}
