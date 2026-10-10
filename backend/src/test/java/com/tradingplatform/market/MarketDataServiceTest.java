package com.tradingplatform.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketDataServiceTest {

    @Test
    void liveIntradayReadsOnlyProviderCandlesAndDoesNotUseHistoricalCache() {
        InstrumentCatalogService catalog = mock(InstrumentCatalogService.class);
        UpstoxMarketDataProvider upstox = mock(UpstoxMarketDataProvider.class);
        Instrument nifty = new Instrument(
                "NSE_INDEX|Nifty 50", "NIFTY", "Nifty 50", "NSE", "NSE_INDEX", null, true);
        Instant latest = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        when(catalog.resolveSymbol(nifty.instrumentKey())).thenReturn(nifty);
        when(upstox.getIntradayCandles(nifty.instrumentKey(), Timeframe.M1))
                .thenReturn(List.of(candle(latest)));
        MarketDataService service = new MarketDataService(
                catalog, upstox, mock(MockMarketDataProvider.class), mock(MarketDataCache.class),
                mock(CandlePersistenceService.class), "upstox", 10, 500, 300);

        List<Candle> candles = service.liveIntraday(nifty.instrumentKey(), Timeframe.M1);

        assertEquals(List.of(latest), candles.stream().map(Candle::timestamp).toList());
        verify(upstox).getIntradayCandles(nifty.instrumentKey(), Timeframe.M1);
    }

    @Test
    void liveIntradayDoesNotPretendMockDataIsLive() {
        MarketDataService service = new MarketDataService(
                mock(InstrumentCatalogService.class), mock(UpstoxMarketDataProvider.class),
                mock(MockMarketDataProvider.class), mock(MarketDataCache.class),
                mock(CandlePersistenceService.class), "mock", 10, 500, 300);

        assertThrows(MarketDataUnavailableException.class,
                () -> service.liveIntraday("NSE_INDEX|Nifty 50", Timeframe.M1));
    }

    @Test
    void intradayRefreshesProviderEvenWhenCacheAlreadyHasEnoughCandles() {
        InstrumentCatalogService catalog = mock(InstrumentCatalogService.class);
        UpstoxMarketDataProvider upstox = mock(UpstoxMarketDataProvider.class);
        MarketDataCache cache = mock(MarketDataCache.class);
        CandlePersistenceService persistence = mock(CandlePersistenceService.class);
        Instrument nifty = new Instrument(
                "NSE_INDEX|Nifty 50", "NIFTY", "Nifty 50", "NSE", "NSE_INDEX", null, true);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        List<Candle> cached = java.util.stream.IntStream.range(1, 61)
                .mapToObj(index -> candle(now.minus(index * 5L, ChronoUnit.MINUTES)))
                .toList();
        Candle newest = candle(now);
        when(catalog.resolveSymbol("NIFTY")).thenReturn(nifty);
        when(cache.getCandles(nifty.instrumentKey(), Timeframe.M5, "intraday"))
                .thenReturn(Optional.of(cached));
        when(upstox.getIntradayCandles(nifty.instrumentKey(), Timeframe.M5))
                .thenReturn(List.of(newest));
        MarketDataService service = new MarketDataService(
                catalog, upstox, mock(MockMarketDataProvider.class), cache, persistence,
                "upstox", 10, 500, 300);

        List<Candle> result = service.intraday("NIFTY", Timeframe.M5);

        assertEquals(now, result.get(result.size() - 1).timestamp());
        verify(upstox).getIntradayCandles(nifty.instrumentKey(), Timeframe.M5);
        verify(persistence).upsert(eq(nifty.instrumentKey()), eq(Timeframe.M5), any());
        verify(cache).putCandles(eq(nifty.instrumentKey()), eq(Timeframe.M5), eq("intraday"), any());
    }

    @Test
    void intradayDoesNotMaskLiveProviderFailureWithCachedCandles() {
        InstrumentCatalogService catalog = mock(InstrumentCatalogService.class);
        UpstoxMarketDataProvider upstox = mock(UpstoxMarketDataProvider.class);
        MarketDataCache cache = mock(MarketDataCache.class);
        Instrument nifty = new Instrument(
                "NSE_INDEX|Nifty 50", "NIFTY", "Nifty 50", "NSE", "NSE_INDEX", null, true);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        List<Candle> cached = java.util.stream.IntStream.range(1, 61)
                .mapToObj(index -> candle(now.minus(index * 5L, ChronoUnit.MINUTES)))
                .toList();
        when(catalog.resolveSymbol("NIFTY")).thenReturn(nifty);
        when(cache.getCandles(nifty.instrumentKey(), Timeframe.M5, "intraday"))
                .thenReturn(Optional.of(cached));
        when(upstox.getIntradayCandles(nifty.instrumentKey(), Timeframe.M5))
                .thenThrow(new MarketDataUnavailableException("Upstox is unavailable."));
        MarketDataService service = new MarketDataService(
                catalog, upstox, mock(MockMarketDataProvider.class), cache,
                mock(CandlePersistenceService.class), "upstox", 10, 500, 300);

        MarketDataUnavailableException error = assertThrows(MarketDataUnavailableException.class,
                () -> service.intraday("NIFTY", Timeframe.M5));

        assertEquals("Upstox is unavailable.", error.getMessage());
    }

    @Test
    void trainingHistoryUsesNonOverlappingThirtyDayChunksForOneYear() {
        InstrumentCatalogService catalog = mock(InstrumentCatalogService.class);
        UpstoxMarketDataProvider upstox = mock(UpstoxMarketDataProvider.class);
        MockMarketDataProvider mockProvider = mock(MockMarketDataProvider.class);
        MarketDataCache cache = mock(MarketDataCache.class);
        CandlePersistenceService persistence = mock(CandlePersistenceService.class);
        when(catalog.resolveSymbol("NIFTY")).thenReturn(new Instrument(
                "NSE_INDEX|Nifty 50", "NIFTY", "Nifty 50", "NSE", "NSE_INDEX", null, true));
        when(upstox.getHistoricalCandles(eq("NSE_INDEX|Nifty 50"), eq(Timeframe.M5), any(), any()))
                .thenAnswer(invocation -> {
                    LocalDate start = invocation.getArgument(2);
                    return List.of(candle(start.atStartOfDay().toInstant(ZoneOffset.UTC)));
                });

        MarketDataService service = new MarketDataService(
                catalog, upstox, mockProvider, cache, persistence, "upstox", 10, 500, 300);
        LocalDate requestedFrom = LocalDate.of(2025, 10, 1);
        LocalDate requestedTo = LocalDate.of(2026, 10, 1);

        List<Candle> result = service.fetchHistoricalForTraining("NIFTY", Timeframe.M5, requestedFrom, requestedTo);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(upstox, org.mockito.Mockito.times(13)).getHistoricalCandles(
                eq("NSE_INDEX|Nifty 50"), eq(Timeframe.M5), fromCaptor.capture(), toCaptor.capture());
        List<LocalDate> chunkStarts = fromCaptor.getAllValues();
        List<LocalDate> chunkEnds = toCaptor.getAllValues();
        assertEquals(requestedFrom, chunkStarts.get(0));
        assertEquals(requestedTo, chunkEnds.get(chunkEnds.size() - 1));
        for (int index = 0; index < chunkStarts.size(); index++) {
            assertTrue(ChronoUnit.DAYS.between(chunkStarts.get(index), chunkEnds.get(index)) < 30);
            if (index > 0) {
                assertEquals(chunkEnds.get(index - 1).plusDays(1), chunkStarts.get(index));
            }
        }
        assertEquals(13, result.size());
        verify(persistence).upsert(eq("NSE_INDEX|Nifty 50"), eq(Timeframe.M5), any());
    }

    @Test
    void trainingHistoryDeduplicatesAndSortsChunkResults() {
        InstrumentCatalogService catalog = mock(InstrumentCatalogService.class);
        UpstoxMarketDataProvider upstox = mock(UpstoxMarketDataProvider.class);
        when(catalog.resolveSymbol("NIFTY")).thenReturn(new Instrument(
                "NSE_INDEX|Nifty 50", "NIFTY", "Nifty 50", "NSE", "NSE_INDEX", null, true));
        Instant first = Instant.parse("2026-09-01T03:45:00Z");
        Instant second = first.plus(5, ChronoUnit.MINUTES);
        when(upstox.getHistoricalCandles(eq("NSE_INDEX|Nifty 50"), eq(Timeframe.M5), any(), any()))
                .thenReturn(List.of(candle(second), candle(first), candle(first)));
        MarketDataService service = new MarketDataService(
                catalog, upstox, mock(MockMarketDataProvider.class), mock(MarketDataCache.class),
                mock(CandlePersistenceService.class), "upstox", 10, 500, 300);

        List<Candle> result = service.fetchHistoricalForTraining(
                "NIFTY", Timeframe.M5, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

        assertEquals(List.of(first, second), result.stream().map(Candle::timestamp).toList());
    }

    private static Candle candle(Instant timestamp) {
        BigDecimal price = BigDecimal.valueOf(100);
        return new Candle(timestamp, price, price, price, price, BigDecimal.ONE);
    }
}