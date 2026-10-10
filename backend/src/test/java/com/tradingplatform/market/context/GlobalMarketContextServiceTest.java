package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.InstrumentCatalogService;
import com.tradingplatform.market.MockMarketDataProvider;
import com.tradingplatform.market.Quote;
import com.tradingplatform.market.UpstoxMarketDataProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GlobalMarketContextServiceTest {

    @Test
    void usesConfiguredUpstoxInstrumentKeysForExistingGlobalFeatureContext() {
        InstrumentCatalogService catalog = mock(InstrumentCatalogService.class);
        UpstoxMarketDataProvider upstox = mock(UpstoxMarketDataProvider.class);
        MockMarketDataProvider mockProvider = mock(MockMarketDataProvider.class);
        UpstoxGlobalInstrumentProperties instruments = new UpstoxGlobalInstrumentProperties(
                "GLOBAL_INDEX|SGX NIFTY",
                "GLOBAL_INDEX|^GSPC",
                "GLOBAL_INDEX|IXIX",
                "GLOBAL_INDEX|^DJI",
                "GLOBAL_INDEX|^N225",
                "GLOBAL_INDEX|^HSI",
                "NCD_FO|USDINR",
                "NSE_COM|CRUDEOIL",
                "",
                ""
        );
        when(upstox.getQuote(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument instrument = invocation.getArgument(0);
            return new Quote(instrument.symbol(), instrument.instrumentKey(),
                    new BigDecimal("110"), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO,
                    new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("10"),
                    BigDecimal.ONE, Instant.now());
        });
        GlobalMarketContextService service = new GlobalMarketContextService(
                catalog, upstox, mockProvider, instruments, "upstox", 900,
                "", "", "", "", "", "", "", "", "", "");

        GlobalMarketContext context = service.snapshot(Instant.now());

        assertEquals(0.1, context.sp500Return());
        assertEquals(0.1, context.nasdaqReturn());
        assertEquals(0.1, context.asianMarketReturn());
        assertEquals(0.1, context.crudeReturn());

        ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
        verify(upstox, org.mockito.Mockito.times(8)).getQuote(captor.capture());
        Set<String> requestedKeys = captor.getAllValues().stream()
                .map(Instrument::instrumentKey)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                instruments.giftNifty(), instruments.sp500(), instruments.usTech100(),
                instruments.dow(), instruments.nikkei(), instruments.hangSeng(),
                instruments.usdInr(), instruments.crudeOil()
        ), requestedKeys);
    }
}
