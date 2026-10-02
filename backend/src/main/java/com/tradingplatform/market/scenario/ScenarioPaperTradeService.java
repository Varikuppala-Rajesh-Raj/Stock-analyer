package com.tradingplatform.market.scenario;

import com.tradingplatform.paper.PaperDtos;
import com.tradingplatform.paper.PaperSide;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class ScenarioPaperTradeService {

    public PaperDtos.OrderRequest buildOrderRequest(
            MarketScenario scenario,
            String symbol,
            int quantity,
            BigDecimal entryPrice,
            BigDecimal stopLoss,
            BigDecimal targetPrice
    ) {
        if (scenario == null || symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Scenario and option symbol are required to build a paper order.");
        }

        if (entryPrice == null || stopLoss == null || targetPrice == null) {
            throw new IllegalArgumentException("Entry, stop, and target prices are required.");
        }

        PaperSide side = switch (scenario.getDirection()) {
            case "UP" -> PaperSide.BUY;
            case "DOWN" -> PaperSide.BUY;
            default -> throw new IllegalStateException("Only directional scenarios can be converted into paper orders.");
        };

        return new PaperDtos.OrderRequest(symbol, side, quantity, stopLoss, targetPrice);
    }
}
