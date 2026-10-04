package com.tradingplatform.controller;

import com.tradingplatform.paper.PaperDryRunService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paper/dry-run")
public class PaperDryRunController {
    private final PaperDryRunService dryRun;

    public PaperDryRunController(PaperDryRunService dryRun) {
        this.dryRun = dryRun;
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return dryRun.settings();
    }

    @GetMapping("/coverage")
    public Map<String, Object> coverage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dryRun.coverage(date);
    }

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody RunRequest request) {
        if (request == null) throw new IllegalArgumentException("Dry-run settings are required.");
        return dryRun.run(request.date(), request.virtualCapital(), request.lotSize());
    }

    public record RunRequest(LocalDate date, BigDecimal virtualCapital, Integer lotSize) {}
}
