package com.tradingplatform.market.websocket;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/market-stream")
public class MarketStreamTestController {

    private final UpstoxMarketStreamService streamService;

    public MarketStreamTestController(UpstoxMarketStreamService streamService) {
        this.streamService = streamService;
    }

    @PostMapping("/connect")
    public String connect() {
        streamService.connect();

        return "connect() called. Current status: " + streamService.status();
    }

    @PostMapping("/disconnect")
    public String disconnect() {
        streamService.disconnect();

        return "disconnect() called. Current status: " + streamService.status();
    }

    @RequestMapping("/status")
    public String status() {
        return streamService.status().toString();
    }
}