package com.tradingplatform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.options.OptionContractSelector;
import com.tradingplatform.persistence.NseFuturesObservationEntity;
import com.tradingplatform.persistence.OptionMarketSnapshotEntity;
import com.tradingplatform.persistence.NseFuturesObservationRepository;
import com.tradingplatform.persistence.OptionMarketSnapshotRepository;
import com.tradingplatform.signal.AnalysisService;
import com.tradingplatform.signal.Signal;
import com.tradingplatform.signal.SignalResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaperDryRunServiceTest {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private final MarketDataService marketData = mock(MarketDataService.class);
    private final AnalysisService analysis = mock(AnalysisService.class);
    private final OptionMarketSnapshotRepository optionSnapshots = mock(OptionMarketSnapshotRepository.class);
    private final NseFuturesObservationRepository futuresObservations = mock(NseFuturesObservationRepository.class);
    private final PaperDryRunService service = new PaperDryRunService(
            marketData,
            analysis,
            new OptionContractSelector(bd("3"), bd("100"), bd("1000"), bd("1"), bd("0.25"), bd("0.75")),
            optionSnapshots,
            futuresObservations,
            new ObjectMapper(),
            bd("30"),
            bd("2"),
            bd("1.5"),
            bd("2"),
            bd("0.2"),
            bd("1"),
            bd("20"),
            bd("5000"),
            bd("300"),
            bd("0"),
            bd("0.0003"),
            10,
            50,
            65,
            240,
            bd("10000"));

    @Test
    void rejectsTodayBeforeQueryingMarketHistory() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        assertThrows(IllegalArgumentException.class,
                () -> service.run(today, bd("10000"), 65));

        verifyNoInteractions(optionSnapshots, futuresObservations, marketData, analysis);
    }

    @Test
    void reportsMissingOptionSnapshotsAsUnavailableReplayData() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        when(optionSnapshots.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                org.mockito.ArgumentMatchers.eq("NIFTY"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        HistoricalReplayDataUnavailableException exception = assertThrows(
                HistoricalReplayDataUnavailableException.class,
                () -> service.run(date, bd("10000"), 65));

        assertEquals(true, exception.getMessage().contains("DATA INSUFFICIENT"));
        verify(optionSnapshots).findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                org.mockito.ArgumentMatchers.eq("NIFTY"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(futuresObservations).findByTimestampBetweenOrderByTimestampAscIdAsc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(analysis);
        verify(marketData).persistedHistory("NIFTY", Timeframe.M5, date.minusDays(10), date);
        verify(marketData, never()).history(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void coverageUsesIstTradingDayAndAcceptsCompletePersistedData() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        List<Candle> candles = spotCandles(date);
        List<OptionMarketSnapshotEntity> options = optionSnapshots(date, null);
        List<NseFuturesObservationEntity> futures = futuresObservations(date, null);
        when(optionSnapshots.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                org.mockito.ArgumentMatchers.eq("NIFTY"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(options);
        when(futuresObservations.findByTimestampBetweenOrderByTimestampAscIdAsc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(futures);
        when(marketData.persistedHistory("NIFTY", Timeframe.M5, date.minusDays(10), date))
                .thenReturn(candles);

        Map<String, Object> coverage = service.coverage(date);

        assertEquals(true, coverage.get("ready"));
        assertEquals("DATA_SUFFICIENT", coverage.get("status"));
        assertEquals("Asia/Kolkata", coverage.get("timezone"));
        assertEquals(75, coverage.get("expectedIntervals"));
        assertEquals(75, coverage.get("optionIntervals"));
        assertEquals(75, coverage.get("spotIntervals"));
        assertEquals(75, coverage.get("futuresIntervals"));
        assertEquals(date.atTime(9, 20).atZone(INDIA).toInstant(), coverage.get("firstSnapshot"));
        assertEquals(date.atTime(9, 15).atZone(INDIA).toInstant(), coverage.get("firstSpotCandle"));
        verify(optionSnapshots).findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                "NIFTY", date.atStartOfDay(INDIA).toInstant(),
                date.plusDays(1).atStartOfDay(INDIA).toInstant().minusNanos(1));
    }

    @Test
    void insufficientFuturesCoverageBlocksRunWithoutRunningStrategy() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        List<Candle> candles = spotCandles(date);
        List<OptionMarketSnapshotEntity> options = optionSnapshots(date, null);
        List<NseFuturesObservationEntity> futures = futuresObservations(date, LocalTime.of(10, 0));
        futures.removeIf(row -> {
            LocalTime time = row.timestamp.atZone(INDIA).toLocalTime();
            return time.equals(LocalTime.of(10, 5))
                    || time.equals(LocalTime.of(10, 10))
                    || time.equals(LocalTime.of(10, 15));
        });
        futures.add(futuresObservation(date.atTime(15, 40).atZone(INDIA).toInstant(), "20000"));
        when(optionSnapshots.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                org.mockito.ArgumentMatchers.eq("NIFTY"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(options);
        when(futuresObservations.findByTimestampBetweenOrderByTimestampAscIdAsc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(futures);
        when(marketData.persistedHistory("NIFTY", Timeframe.M5, date.minusDays(10), date))
                .thenReturn(candles);

        Map<String, Object> coverage = service.coverage(date);

        assertEquals(false, coverage.get("ready"));
        assertEquals("DATA_INSUFFICIENT", coverage.get("status"));
        assertEquals(70, coverage.get("futuresIntervals"));
        assertEquals(72, coverage.get("futuresObservations"));
        assertEquals(List.of("10:00", "10:05", "10:10", "10:15", "10:20"),
                coverage.get("missingFuturesIntervals"));
        assertThrows(HistoricalReplayDataUnavailableException.class,
                () -> service.run(date, bd("10000"), 65));
        verifyNoInteractions(analysis);
    }

    @Test
    void incompleteOptionIntervalIsReportedAndReplayDoesNotSelectFromIt() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        LocalTime missing = LocalTime.of(10, 0);
        List<Candle> candles = spotCandles(date);
        List<OptionMarketSnapshotEntity> options = optionSnapshots(date, missing);
        List<NseFuturesObservationEntity> futures = futuresObservations(date, null);
        when(optionSnapshots.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                org.mockito.ArgumentMatchers.eq("NIFTY"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(options);
        when(futuresObservations.findByTimestampBetweenOrderByTimestampAscIdAsc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(futures);
        when(marketData.persistedHistory("NIFTY", Timeframe.M5, date.minusDays(10), date))
                .thenReturn(candles);
        when(analysis.analyze(org.mockito.ArgumentMatchers.eq("NIFTY"),
                org.mockito.ArgumentMatchers.eq(Timeframe.M5), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new SignalResult("NIFTY", Signal.HOLD, 0, 0, 0, bd("20000"),
                        bd("19900"), bd("20200"), bd("2"), List.of(), List.of()));

        Map<String, Object> coverage = service.coverage(date);

        assertEquals(true, coverage.get("ready"));
        assertEquals(74, coverage.get("optionIntervals"));
        assertEquals(List.of("10:00"), coverage.get("missingOptionIntervals"));

        Map<String, Object> result = service.run(date, bd("10000"), 65);
        Map<?, ?> missingCycle = (Map<?, ?>) ((List<?>) result.get("cycles")).get(9);
        assertEquals(date.atTime(missing).atZone(INDIA).toInstant(), missingCycle.get("timestamp"));
        assertEquals("NO_TRADE", missingCycle.get("status"));
        assertEquals("HISTORICAL_OPTION_SNAPSHOT_UNAVAILABLE", missingCycle.get("reason"));
        Map<?, ?> summary = (Map<?, ?>) result.get("summary");
        assertEquals(0, summary.get("tradeEntries"));
    }

    @Test
    void includesEstimatedExitChargesForOpenTradesInSummary() {
        LocalDate date = LocalDate.now(INDIA).minusDays(1);
        Instant decisionAt = date.atTime(12, 0).atZone(INDIA).toInstant();
        List<OptionMarketSnapshotEntity> snapshots = optionSnapshots(date, null);
        List<NseFuturesObservationEntity> futures = futuresObservations(date, null);
        List<Candle> candles = spotCandles(date);
        Instant warmupStart = date.atTime(7, 0).atZone(INDIA).toInstant();
        for (int index = 0; index < 60; index++) {
            Instant timestamp = warmupStart.plus(Duration.ofMinutes(index * 5L));
            candles.add(new Candle(timestamp, bd("20000"), bd("20010"), bd("19990"),
                    bd("20000"), bd("1000")));
        }
        snapshots.add(optionSnapshot(date, decisionAt));
        futures.add(futuresObservation(decisionAt, "20010"));
        when(optionSnapshots.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                org.mockito.ArgumentMatchers.eq("NIFTY"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(snapshots);
        when(futuresObservations.findByTimestampBetweenOrderByTimestampAscIdAsc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(futures);
        when(marketData.history("NIFTY", Timeframe.M5, date.minusDays(10), date)).thenReturn(candles);
        when(marketData.persistedHistory("NIFTY", Timeframe.M5, date.minusDays(10), date))
                .thenReturn(candles);
        when(analysis.analyze(org.mockito.ArgumentMatchers.eq("NIFTY"),
                org.mockito.ArgumentMatchers.eq(Timeframe.M5), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new SignalResult("NIFTY", Signal.BUY, 70, 0, 70, bd("20000"),
                        bd("19900"), bd("20200"), bd("2"), List.of(), List.of()));

        Map<String, Object> result = service.run(date, bd("10000"), 65);
        Map<?, ?> summary = (Map<?, ?>) result.get("summary");
        Map<?, ?> trade = (Map<?, ?>) ((List<?>) result.get("trades")).get(0);

        assertEquals(bd("0.40"), summary.get("charges"));
        assertEquals(summary.get("charges"), trade.get("charges"));
        assertEquals(0, bd("-0.40").compareTo((BigDecimal) summary.get("unrealizedPnl")));
    }

    private static OptionMarketSnapshotEntity optionSnapshot(LocalDate date, Instant timestamp) {
        OptionMarketSnapshotEntity snapshot = new OptionMarketSnapshotEntity();
        snapshot.timestamp = timestamp;
        snapshot.symbol = "NIFTY";
        snapshot.expiry = date.plusDays(7);
        snapshot.strikePrice = bd("20000");
        snapshot.optionType = "CE";
        snapshot.instrumentKey = "NIFTY-TEST-CE";
        snapshot.underlyingSpot = bd("20000");
        snapshot.ltp = bd("10");
        snapshot.bid = bd("9.95");
        snapshot.ask = bd("10.05");
        snapshot.volume = bd("1000");
        snapshot.oi = bd("10000");
        snapshot.delta = bd("0.5");
        return snapshot;
    }

    private static List<OptionMarketSnapshotEntity> optionSnapshots(LocalDate date, LocalTime missingTime) {
        List<OptionMarketSnapshotEntity> snapshots = new ArrayList<>();
        for (LocalTime time = LocalTime.of(9, 20); !time.isAfter(LocalTime.of(15, 30)); time = time.plusMinutes(5)) {
            if (time.equals(missingTime)) continue;
            snapshots.add(optionSnapshot(date, date.atTime(time).atZone(INDIA).toInstant()));
        }
        return snapshots;
    }

    private static List<NseFuturesObservationEntity> futuresObservations(LocalDate date, LocalTime missingTime) {
        List<NseFuturesObservationEntity> observations = new ArrayList<>();
        for (LocalTime time = LocalTime.of(9, 15); !time.isAfter(LocalTime.of(15, 30)); time = time.plusMinutes(5)) {
            if (time.equals(missingTime)) continue;
            observations.add(futuresObservation(date.atTime(time).atZone(INDIA).toInstant(), "20000"));
        }
        return observations;
    }

    private static List<Candle> spotCandles(LocalDate date) {
        List<Candle> candles = new ArrayList<>();
        for (LocalTime time = LocalTime.of(9, 15); !time.isAfter(LocalTime.of(15, 25)); time = time.plusMinutes(5)) {
            Instant timestamp = date.atTime(time).atZone(INDIA).toInstant();
            candles.add(new Candle(timestamp, bd("20000"), bd("20010"), bd("19990"),
                    bd("20000"), bd("1000")));
        }
        return candles;
    }

    private static NseFuturesObservationEntity futuresObservation(Instant timestamp, String price) {
        NseFuturesObservationEntity observation = new NseFuturesObservationEntity();
        observation.timestamp = timestamp;
        observation.identifier = "NIFTY-FUT";
        observation.expiry = "2026-12-31";
        observation.lastPrice = bd(price);
        return observation;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
