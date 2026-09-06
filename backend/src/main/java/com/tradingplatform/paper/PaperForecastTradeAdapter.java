package com.tradingplatform.paper;

import com.tradingplatform.paper.PaperDtos.Order;
import com.tradingplatform.paper.PaperDtos.OrderRequest;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Converts verified forecasts into paper orders and never calls a live broker. */
@Service
public class PaperForecastTradeAdapter {
    private final PaperTradingEngine paper;
    private final boolean enabled;
    private final double minimumConfidence;

    public PaperForecastTradeAdapter(
            PaperTradingEngine paper,
            @Value("${trading.paper.forecast-enabled:false}") boolean enabled,
            @Value("${trading.paper.forecast-min-confidence:0.60}") double minimumConfidence
    ) {
        this.paper = paper;
        this.enabled = enabled;
        this.minimumConfidence = minimumConfidence;
    }

    public Order submit(ForecastTradeRequest forecast) {
        if (!enabled) throw new IllegalStateException("Forecast paper trading is disabled");
        if (forecast == null || forecast.symbol() == null || forecast.symbol().isBlank()) {
            throw new IllegalArgumentException("Forecast symbol is required");
        }
        if (!Double.isFinite(forecast.confidence()) || forecast.confidence() < minimumConfidence) {
            throw new IllegalArgumentException("Forecast confidence is below the paper-trading threshold");
        }
        if (forecast.direction() == null || forecast.quantity() < 1 || forecast.entryPrice() == null || forecast.entryPrice().signum() <= 0) {
            throw new IllegalArgumentException("Forecast quantity and positive entry price are required");
        }
        PaperSide side = switch (forecast.direction().toUpperCase()) {
            case "UP" -> PaperSide.BUY;
            case "DOWN" -> PaperSide.SELL;
            default -> throw new IllegalArgumentException("Only UP or DOWN forecasts can create a paper trade");
        };
        return paper.submit(new OrderRequest(forecast.symbol(), side, forecast.quantity(), forecast.stopLoss(), forecast.target()));
    }

    public record ForecastTradeRequest(String symbol, String direction, double confidence,
                                       int quantity, BigDecimal entryPrice,
                                       BigDecimal stopLoss, BigDecimal target) {
    }
}