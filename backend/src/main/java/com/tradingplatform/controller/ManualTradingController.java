package com.tradingplatform.controller;

import com.tradingplatform.paper.ManualTradingService;
import com.tradingplatform.paper.ManualTradeDtos.Portfolio;
import com.tradingplatform.paper.ManualTradeDtos.Trade;
import com.tradingplatform.paper.ManualTradeDtos.TradeRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manual-trading")
public class ManualTradingController {
    private final ManualTradingService trading;

    public ManualTradingController(ManualTradingService trading) {
        this.trading = trading;
    }

    @GetMapping
    public Portfolio portfolio() {
        return trading.portfolio();
    }

    @PostMapping("/trades")
    @ResponseStatus(HttpStatus.CREATED)
    public Trade buy(@RequestBody TradeRequest request) {
        return trading.buy(request);
    }

    @PostMapping("/trades/{id}/sell")
    public Trade sell(@PathVariable UUID id) {
        return trading.sell(id);
    }
}
