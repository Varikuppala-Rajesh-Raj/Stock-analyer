package com.tradingplatform.paper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaperForecastTradeAdapterTest {
    @Test
    void mapsHighConfidenceUpForecastToExistingPaperBuyPath() {
        PaperTradingEngine engine = mock(PaperTradingEngine.class);
        PaperForecastTradeAdapter adapter = new PaperForecastTradeAdapter(engine, true, 0.6);
        var request = new PaperForecastTradeAdapter.ForecastTradeRequest("NIFTY", "UP", 0.8, 1,
                new BigDecimal("100"), new BigDecimal("98"), new BigDecimal("104"));

        adapter.submit(request);

        verify(engine).submit(argThat(order -> order.side() == PaperSide.BUY
                && order.quantity() == 1 && order.symbol().equals("NIFTY")));
    }

    @Test
    void rejectsDisabledLowConfidenceAndNeutralForecasts() {
        PaperTradingEngine engine = mock(PaperTradingEngine.class);
        var request = new PaperForecastTradeAdapter.ForecastTradeRequest("NIFTY", "UP", 0.8, 1,
                new BigDecimal("100"), null, null);
        assertThrows(IllegalStateException.class, () -> new PaperForecastTradeAdapter(engine, false, 0.6).submit(request));
        assertThrows(IllegalArgumentException.class, () -> new PaperForecastTradeAdapter(engine, true, 0.9).submit(request));
        var neutral = new PaperForecastTradeAdapter.ForecastTradeRequest("NIFTY", "NEUTRAL", 0.99, 1,
                new BigDecimal("100"), null, null);
        assertThrows(IllegalArgumentException.class, () -> new PaperForecastTradeAdapter(engine, true, 0.6).submit(neutral));
    }
}