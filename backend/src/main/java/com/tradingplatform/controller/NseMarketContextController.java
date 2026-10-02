package com.tradingplatform.controller;

import com.tradingplatform.market.nse.NseMarketContextService;
import com.tradingplatform.market.nse.NseMarketContextSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nse")
public class NseMarketContextController {
    private final NseMarketContextService marketContext;

    public NseMarketContextController(NseMarketContextService marketContext) {
        this.marketContext = marketContext;
    }

    @GetMapping("/market-context")
    public NseMarketContextSnapshot marketContext() {
        return marketContext.snapshot();
    }
}