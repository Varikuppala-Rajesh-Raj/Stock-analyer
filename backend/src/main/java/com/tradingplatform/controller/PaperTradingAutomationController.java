package com.tradingplatform.controller;

import com.tradingplatform.paper.PaperTradingAutomationService;
import com.tradingplatform.paper.NiftyPaperTradingCycleService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paper/automation")
public class PaperTradingAutomationController {

    private final PaperTradingAutomationService automationService;
    private final NiftyPaperTradingCycleService cycleService;

    public PaperTradingAutomationController(
            PaperTradingAutomationService automationService,
            NiftyPaperTradingCycleService cycleService
    ) {
        this.automationService = automationService;
        this.cycleService = cycleService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("automation", automationService.status(), "cycle", cycleService.status());
    }

    @PostMapping("/cycle")
    public com.tradingplatform.paper.NiftyPaperTradingCycleResult runNiftyCycle() {
        return cycleService.runOnce();
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        automationService.stop();
        return status();
    }

    @PostMapping("/resume")
    public Map<String, Object> resume() {
        automationService.resume();
        return status();
    }

    @GetMapping("/cycles")
    public java.util.List<com.tradingplatform.persistence.PaperAutomationCycleEntity> recentCycles() {
        return cycleService.recentCycles();
    }

}
