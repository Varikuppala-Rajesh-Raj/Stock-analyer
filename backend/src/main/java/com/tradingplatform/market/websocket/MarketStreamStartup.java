package com.tradingplatform.market.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MarketStreamStartup {

    private final MarketStreamService upstoxStream;
    private final MarketStreamService mockStream;
    private final String provider;

    public MarketStreamStartup(
            @org.springframework.beans.factory.annotation.Qualifier("upstoxMarketStreamService")
            MarketStreamService upstoxStream,

            @org.springframework.beans.factory.annotation.Qualifier("mockMarketStreamService")
            MarketStreamService mockStream,

            @Value("${trading.MARKET_DATA_PROVIDER:mock}")
            String provider
    ) {
        this.upstoxStream = upstoxStream;
        this.mockStream = mockStream;
        this.provider = provider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startMarketStream() {

        if ("upstox".equalsIgnoreCase(provider)) {
            upstoxStream.connect();
        } else {
            mockStream.connect();
        }
    }
}