package com.tradingplatform.paper;

import com.tradingplatform.market.websocket.MarketTick;
import com.tradingplatform.market.websocket.MarketTickWebSocketHandler;
import org.springframework.stereotype.Component;

@Component
public class PaperTickListener {
    private final PaperPriceService prices;
    private final PaperTradingEngine engine;
    private final MarketTickWebSocketHandler browserTicks;

    public PaperTickListener(
            PaperPriceService prices,
            PaperTradingEngine engine,
            MarketTickWebSocketHandler browserTicks) {
        this.prices = prices;
        this.engine = engine;
        this.browserTicks = browserTicks;
    }

    public void accept(MarketTick tick) {
        prices.accept(tick);
        engine.onPrice(tick.instrumentKey(), tick.lastPrice());
        browserTicks.publish(tick);
    }
}
