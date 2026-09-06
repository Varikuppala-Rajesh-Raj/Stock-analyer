package com.tradingplatform.config;

import com.tradingplatform.market.websocket.MarketTickWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class MarketWebSocketConfig implements WebSocketConfigurer {

    private final MarketTickWebSocketHandler marketTicks;

    public MarketWebSocketConfig(MarketTickWebSocketHandler marketTicks) {
        this.marketTicks = marketTicks;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketTicks, "/ws/market")
                .setAllowedOriginPatterns("http://localhost:5173", "http://localhost:8080");
    }
}
