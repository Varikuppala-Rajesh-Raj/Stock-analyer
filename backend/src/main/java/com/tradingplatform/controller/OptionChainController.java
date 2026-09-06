package com.tradingplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.options.NiftyOptionChainService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/options")
public class OptionChainController {

    private final NiftyOptionChainService optionChainService;

    public OptionChainController(
            NiftyOptionChainService optionChainService
    ) {
        this.optionChainService =
                optionChainService;
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
}
