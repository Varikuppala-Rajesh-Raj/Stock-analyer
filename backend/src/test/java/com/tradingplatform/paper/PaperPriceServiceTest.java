package com.tradingplatform.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.MarketDataUnavailableException;
import com.tradingplatform.market.Quote;
import com.tradingplatform.market.websocket.MarketTick;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaperPriceServiceTest {
    private final Instrument instrument = new Instrument(
            "NSE_FO|OPTION", "OPTION", "Option", "NSE", "NSE_FO", null, true);

    @Test
    void usesFreshStreamPriceWithoutCallingRestProvider() {
        MarketDataService market = mock(MarketDataService.class);
        PaperPriceService service = new PaperPriceService(market, 30);
        service.accept(new MarketTick("NSE_FO|OPTION", Instant.now(), BigDecimal.valueOf(100),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(100), BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(100)));

        assertEquals(BigDecimal.valueOf(100), service.current(instrument).value());
        verifyNoInteractions(market);
    }

    @Test
    void fallsBackToFreshQuoteWhenLastStreamTickHasExpired() {
        MarketDataService market = mock(MarketDataService.class);
        when(market.quote(instrument)).thenReturn(new Quote("OPTION", "NSE_FO|OPTION",
                BigDecimal.valueOf(101), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()));
        PaperPriceService service = new PaperPriceService(market, 30);
        service.accept(new MarketTick("NSE_FO|OPTION", Instant.now().minusSeconds(60),
                BigDecimal.valueOf(100), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.valueOf(100)));

        assertEquals(BigDecimal.valueOf(101), service.current(instrument).value());
        verify(market).quote(instrument);
    }

    @Test
    void rejectsStaleRestQuoteInsteadOfUsingOldPrice() {
        MarketDataService market = mock(MarketDataService.class);
        when(market.quote(instrument)).thenReturn(new Quote("OPTION", "NSE_FO|OPTION",
                BigDecimal.valueOf(101), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Instant.now().minusSeconds(60)));
        PaperPriceService service = new PaperPriceService(market, 30);

        assertThrows(MarketDataUnavailableException.class, () -> service.current(instrument));
    }
}
