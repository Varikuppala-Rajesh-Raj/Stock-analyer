package com.tradingplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.options.NiftyOptionChainService;
import com.tradingplatform.market.options.OptionSnapshotService;
import com.tradingplatform.market.options.OptionSnapshotService.OptionPricePoint;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/options")
public class OptionChainController {

    private final NiftyOptionChainService optionChainService;
    private final OptionSnapshotService snapshots;

    public OptionChainController(
            NiftyOptionChainService optionChainService,
            OptionSnapshotService snapshots
    ) {
        this.optionChainService =
                optionChainService;
        this.snapshots = snapshots;
    }

    @GetMapping("/nifty/expiry")
public Map<String, Object> getNearestNiftyExpiry() {
    String expiry = optionChainService
            .nearestNiftyExpiry()
            .orElseThrow(() ->
                    new IllegalStateException(
                            "Unable to determine the nearest NIFTY expiry."
                    )
            );

    return Map.of(
            "status", "success",
            "expiry", expiry
    );
}
@GetMapping("/nifty/expiries")
public Map<String, Object> getNiftyExpiries(
        @RequestParam(defaultValue = "5") int limit
) {
    int safeLimit = Math.min(Math.max(limit, 1), 10);

    return Map.of(
            "status", "success",
            "expiries", optionChainService.niftyExpiries(safeLimit)
    );
}
    @GetMapping("/nifty/chain")
    public JsonNode getNiftyOptionChain(
            @RequestParam String expiry
    ) {
        return optionChainService
                .getNiftyOptionChain(expiry);
    }

    @GetMapping("/nifty/contract-history")
    public List<OptionPricePoint> getNiftyOptionHistory(
            @RequestParam String instrumentKey,
            @RequestParam String expiry,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        LocalDate end = to == null ? LocalDate.now(ZoneId.of("Asia/Kolkata")) : to;
        LocalDate start = from == null ? end.minusDays(7) : from;
        try {
            return snapshots.history(instrumentKey, expiry, start, end);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Expiry must be a valid ISO date.", exception);
        }
    }
}
