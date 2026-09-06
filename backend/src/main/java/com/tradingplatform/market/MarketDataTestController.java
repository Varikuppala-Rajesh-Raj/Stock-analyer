package com.tradingplatform.market;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/market-data")
public class MarketDataTestController {

    private final MarketDataProvider provider;

    public MarketDataTestController( @Qualifier("upstoxMarketDataProvider") MarketDataProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/historical")
    public List<Candle> historical(
            @RequestParam String instrumentKey,
            @RequestParam Timeframe timeframe,
            @RequestParam String from,
            @RequestParam String to) {

        return provider.getHistoricalCandles(
                instrumentKey,
                timeframe,
                LocalDate.parse(from),
                LocalDate.parse(to)
        );
    }
}