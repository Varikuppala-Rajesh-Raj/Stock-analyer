package com.tradingplatform.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.nse.NseMarketContextService;
import com.tradingplatform.market.nse.NseMarketContextSnapshot;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.FuturesContext;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.Metric;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.NiftyContext;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.Status;
import com.tradingplatform.market.options.NiftyOptionChainService;
import com.tradingplatform.market.options.OptionContractSelector;
import com.tradingplatform.market.scenario.ScenarioExecutionService;
import com.tradingplatform.persistence.PaperAutomationCycleEntity;
import com.tradingplatform.persistence.PaperAutomationCycleRepository;
import com.tradingplatform.persistence.PaperOrderRepository;
import com.tradingplatform.signal.AnalysisService;
import com.tradingplatform.signal.Signal;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NiftyPaperTradingCycleServiceTest {
    @Test
    void acceptsContextWithinTheConfigured180SecondFreshnessWindow() {
        Instant now = Instant.parse("2026-10-06T05:37:20Z");
        NseMarketContextSnapshot snapshot = availableContext(now.minusSeconds(120));

        assertNull(NiftyPaperTradingCycleService.niftyContextBlockReason(snapshot, now, Duration.ofSeconds(180)));
    }

    @Test
    void reportsWhichSourceIsTooOldInsteadOfReturningGenericContextFailure() {
        Instant now = Instant.parse("2026-10-06T05:37:20Z");
        NseMarketContextSnapshot snapshot = availableContext(now.minusSeconds(181));

        assertEquals("NIFTY_CONTEXT_TOO_OLD", NiftyPaperTradingCycleService.niftyContextBlockReason(
                snapshot, now, Duration.ofSeconds(180)));
    }

    @Test
    void doesNotRequireFuturesMomentumWhenNiftyContextIsFresh() {
        Instant now = Instant.parse("2026-10-06T05:37:20Z");
        NseMarketContextSnapshot available = availableContext(now);
        FuturesContext futures = available.futures();
        FuturesContext withoutFiveMinuteReturn = new FuturesContext(
                futures.timestamp(), futures.identifier(), futures.instrumentType(), futures.expiry(),
                futures.ltp(), futures.open(), futures.high(), futures.low(), futures.previousClose(),
                futures.change(), futures.changePercent(), futures.overnightReturn(), futures.openingGap(),
                futures.openInterest(), futures.changeInOpenInterest(), futures.percentChangeInOpenInterest(),
                futures.volume(), futures.turnover(), futures.underlying(), futures.underlyingValue(),
                futures.basis(), new Metric(null, Status.COLLECTING), futures.return10m(),
                futures.return15m(), futures.status());
        NseMarketContextSnapshot snapshot = new NseMarketContextSnapshot(
                now, available.nifty(), withoutFiveMinuteReturn, available.futuresLead());

        assertNull(NiftyPaperTradingCycleService.niftyContextBlockReason(
                snapshot, now, Duration.ofSeconds(180)));
    }

    @Test
    void requiresNiftySignalStrengthOfAtLeast60AndUsesItWhenFuturesMomentumIsUnavailable() {
        assertEquals("UP", NiftyPaperTradingCycleService.niftySignalDirection(Signal.BUY, 60));
        assertEquals("DOWN", NiftyPaperTradingCycleService.niftySignalDirection(Signal.SELL, 60));
        assertNull(NiftyPaperTradingCycleService.niftySignalDirection(Signal.BUY, 59));
        assertNull(NiftyPaperTradingCycleService.niftySignalDirection(Signal.HOLD, 99));
        assertNull(NiftyPaperTradingCycleService.niftySignalBlockReason(Signal.BUY, 60));
        assertEquals("NIFTY_SIGNAL_BELOW_ENTRY_THRESHOLD",
                NiftyPaperTradingCycleService.niftySignalBlockReason(Signal.BUY, 59));
        assertEquals("NIFTY_SIGNAL_NOT_DIRECTIONAL",
                NiftyPaperTradingCycleService.niftySignalBlockReason(Signal.HOLD, 62));
        assertFalse(NiftyPaperTradingCycleService.futuresOpposes("UP", null));
        assertFalse(NiftyPaperTradingCycleService.futuresOpposes("DOWN", null));
    }

    @Test
    void vetoesOnlyAvailableFuturesMomentumThatOpposesNiftyDirection() {
        assertTrue(NiftyPaperTradingCycleService.futuresOpposes(
                "UP", new Metric(-0.1, Status.AVAILABLE)));
        assertTrue(NiftyPaperTradingCycleService.futuresOpposes(
                "DOWN", new Metric(0.1, Status.AVAILABLE)));
        assertFalse(NiftyPaperTradingCycleService.futuresOpposes(
                "UP", new Metric(0.1, Status.AVAILABLE)));
        assertFalse(NiftyPaperTradingCycleService.futuresOpposes(
                "DOWN", new Metric(-0.1, Status.AVAILABLE)));
        assertFalse(NiftyPaperTradingCycleService.futuresOpposes(
                "UP", new Metric(-0.1, Status.STALE)));
    }

    @Test
    void acceptsCurrentFiveMinuteCandleUsingItsIntervalAndFeedDelay() {
        Instant now = Instant.parse("2026-10-06T05:37:20Z");
        Candle candle = new Candle(now.minusSeconds(7 * 60L), BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);

        assertTrue(NiftyPaperTradingCycleService.isFreshNiftyCandle(
                candle, now, Duration.ofSeconds(180)));
    }

    @Test
    void stillRejectsNiftyCandlesOlderThanTheirIntervalAndFeedDelay() {
        Instant now = Instant.parse("2026-10-06T05:37:20Z");
        Candle candle = new Candle(now.minusSeconds(8 * 60L + 1), BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);

        assertFalse(NiftyPaperTradingCycleService.isFreshNiftyCandle(
                candle, now, Duration.ofSeconds(180)));
    }

    @Test
    void recordsExplicitDisabledCycleWithoutFetchingMarketData() {
        MarketDataService market = mock(MarketDataService.class);
        PaperAutomationCycleRepository audit = mock(PaperAutomationCycleRepository.class);
        PaperTradingAutomationService automation = new PaperTradingAutomationService(
                mock(ScenarioExecutionService.class), mock(PaperOrderRepository.class),
                mock(PaperAutomationControlService.class), 5, false, 1, 20);
        NiftyPaperTradingCycleService service = new NiftyPaperTradingCycleService(
                market, mock(AnalysisService.class), mock(NseMarketContextService.class),
                mock(NiftyOptionChainService.class), mock(OptionContractSelector.class),
                mock(PaperTradingEngine.class), mock(PaperPriceService.class), automation, audit,
                mock(PaperMarketBiasGate.class), 60,
                BigDecimal.valueOf(30), BigDecimal.valueOf(2), BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(2), BigDecimal.valueOf(.2));

        NiftyPaperTradingCycleResult result = service.runOnce();

        assertEquals("DISABLED", result.status());
        assertEquals("AUTOMATION_DISABLED", result.reason());
        verify(audit).save(any(PaperAutomationCycleEntity.class));
        verifyNoInteractions(market);
    }

    @Test
    void refusesMockMarketDataBeforeUsingSignalOrOptionServices() {
        MarketDataService market = mock(MarketDataService.class);
        when(market.usesLiveProvider()).thenReturn(false);
        PaperAutomationCycleRepository audit = mock(PaperAutomationCycleRepository.class);
        PaperTradingAutomationService automation = new PaperTradingAutomationService(
                mock(ScenarioExecutionService.class), mock(PaperOrderRepository.class),
                mock(PaperAutomationControlService.class), 5, true, 1, 20);
        NiftyPaperTradingCycleService service = new NiftyPaperTradingCycleService(
                market, mock(AnalysisService.class), mock(NseMarketContextService.class),
                mock(NiftyOptionChainService.class), mock(OptionContractSelector.class),
                mock(PaperTradingEngine.class), mock(PaperPriceService.class), automation, audit,
                mock(PaperMarketBiasGate.class), 60,
                BigDecimal.valueOf(30), BigDecimal.valueOf(2), BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(2), BigDecimal.valueOf(.2));

        NiftyPaperTradingCycleResult result = service.runOnce();

        assertEquals("NO_TRADE", result.status());
        assertEquals("LIVE_MARKET_DATA_REQUIRED", result.reason());
        verify(audit).save(any(PaperAutomationCycleEntity.class));
        verify(market).usesLiveProvider();
        verifyNoMoreInteractions(market);
    }

    @Test
    void recommendationIsReadOnlyAndDoesNotRequireAutomationToBeEnabled() {
        MarketDataService market = mock(MarketDataService.class);
        when(market.usesLiveProvider()).thenReturn(false);
        PaperAutomationCycleRepository audit = mock(PaperAutomationCycleRepository.class);
        ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
        PaperTradingAutomationService automation = new PaperTradingAutomationService(
                execution, mock(PaperOrderRepository.class),
                mock(PaperAutomationControlService.class), 5, false, 1, 20);
        NiftyPaperTradingCycleService service = new NiftyPaperTradingCycleService(
                market, mock(AnalysisService.class), mock(NseMarketContextService.class),
                mock(NiftyOptionChainService.class), mock(OptionContractSelector.class),
                mock(PaperTradingEngine.class), mock(PaperPriceService.class), automation, audit,
                mock(PaperMarketBiasGate.class), 60,
                BigDecimal.valueOf(30), BigDecimal.valueOf(2), BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(2), BigDecimal.valueOf(.2));

        NiftyTradeRecommendation recommendation = service.recommendation();

        assertEquals("NO_TRADE", recommendation.status());
        assertEquals("LIVE_MARKET_DATA_REQUIRED", recommendation.reason());
        verify(market).usesLiveProvider();
        verifyNoInteractions(execution, audit);
    }

    private NseMarketContextSnapshot availableContext(Instant timestamp) {
        String sourceTimestamp = java.time.format.DateTimeFormatter
                .ofPattern("dd-MMM-yyyy HH:mm:ss", java.util.Locale.ENGLISH)
                .withZone(java.time.ZoneId.of("Asia/Kolkata"))
                .format(timestamp);
        Metric availableMetric = new Metric(0.1, Status.AVAILABLE);
        NiftyContext nifty = new NiftyContext(sourceTimestamp, 22_687.4, 22_603.25, 22_692.95,
                22_561.6, 22_555.75, 131.65, 0.58, 0.58, 0.58,
                availableMetric, availableMetric, availableMetric, availableMetric, Status.AVAILABLE);
        FuturesContext futures = new FuturesContext(sourceTimestamp, "NIFTY-FUT", "FUTIDX",
                "27-Oct-2026", 22_778.0, 22_660.0, 22_780.0, 22_638.0, 22_620.8,
                157.2, 0.69, 0.69, 0.69, 292_047L, -501L, -0.17, 15_717L,
                23_206_053_840.45, "NIFTY", 22_687.4, 90.6,
                availableMetric, availableMetric, availableMetric, Status.AVAILABLE);
        return new NseMarketContextSnapshot(timestamp, nifty, futures, availableMetric);
    }
}
