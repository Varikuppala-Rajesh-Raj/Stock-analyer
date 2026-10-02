package com.tradingplatform.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataTrainingIngestionTest {

    @Test
    void trainingHistoryRejectsMockProvider() {
        MarketDataService service = service("mock");

        MarketDataUnavailableException exception = assertThrows(
                MarketDataUnavailableException.class,
                () -> service.fetchHistoricalForTraining(
                        "NIFTY",
                        Timeframe.M5,
                        LocalDate.parse("2026-08-01"),
                        LocalDate.parse("2026-09-01")
                )
        );

        assertEquals("Real historical training requires MARKET_DATA_PROVIDER=upstox.", exception.getMessage());
    }

    @Test
    void trainingHistoryFetchesAndPersistsOrderedUpstoxCandles() {
        InstrumentCatalogService catalog = mock(InstrumentCatalogService.class);
        UpstoxMarketDataProvider upstox = mock(UpstoxMarketDataProvider.class);
        CandlePersistenceService persistence = mock(CandlePersistenceService.class);
        Instrument instrument = new Instrument("NSE_INDEX|Nifty 50", "NIFTY", "NIFTY 50", "NSE", "INDEX", "", true);
        LocalDate from = LocalDate.parse("2026-08-01");
        LocalDate to = LocalDate.parse("2026-09-01");
        Candle earlier = candle("2026-08-01T09:15:00Z");
        Candle later = candle("2026-08-01T09:20:00Z");

        when(catalog.resolveSymbol("NIFTY")).thenReturn(instrument);
        when(upstox.getHistoricalCandles(instrument.instrumentKey(), Timeframe.M5, from, from.plusDays(29)))
            .thenReturn(List.of(later));
        when(upstox.getHistoricalCandles(instrument.instrumentKey(), Timeframe.M5, from.plusDays(30), to))
            .thenReturn(List.of(earlier));

        MarketDataService service = new MarketDataService(
                catalog,
                upstox,
                mock(MockMarketDataProvider.class),
                mock(MarketDataCache.class),
                persistence,
                "upstox",
                10,
                500,
                300
        );

        List<Candle> result = service.fetchHistoricalForTraining("NIFTY", Timeframe.M5, from, to);

        assertEquals(List.of(earlier, later), result);
        verify(upstox).getHistoricalCandles(instrument.instrumentKey(), Timeframe.M5, from, from.plusDays(29));
        verify(upstox).getHistoricalCandles(instrument.instrumentKey(), Timeframe.M5, from.plusDays(30), to);
        verify(persistence).upsert(instrument.instrumentKey(), Timeframe.M5, result);
    }

    private MarketDataService service(String provider) {
        return new MarketDataService(
                mock(InstrumentCatalogService.class),
                mock(UpstoxMarketDataProvider.class),
                mock(MockMarketDataProvider.class),
                mock(MarketDataCache.class),
                mock(CandlePersistenceService.class),
                provider,
                10,
                500,
                300
        );
    }

    private Candle candle(String timestamp) {
        BigDecimal price = BigDecimal.valueOf(100);
        return new Candle(Instant.parse(timestamp), price, price, price, price, BigDecimal.ONE);
    }
}