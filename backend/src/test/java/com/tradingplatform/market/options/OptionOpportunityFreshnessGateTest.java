package com.tradingplatform.market.options;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.controller.NseMarketDataController;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.market.nse.NseMarketContextService;
import com.tradingplatform.market.nse.NseMarketContextSnapshot;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.FuturesContext;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.Metric;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.NiftyContext;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.Status;
import com.tradingplatform.market.scenario.MarketScenarioBuilderService;
import com.tradingplatform.signal.AnalysisService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class OptionOpportunityFreshnessGateTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void staleMarketContextReturnsDataInsufficientBeforePrediction() {
        NiftyOptionChainService chains = mock(NiftyOptionChainService.class);
        OptionOpportunityScanner scanner = mock(OptionOpportunityScanner.class);
        MlPredictionService ml = mock(MlPredictionService.class);
        MarketDataService market = mock(MarketDataService.class);
        AnalysisService analysis = mock(AnalysisService.class);
        OptionSnapshotCollector collector = mock(OptionSnapshotCollector.class);
        NseMarketContextService context = mock(NseMarketContextService.class);
        when(context.snapshot()).thenReturn(snapshot(Status.STALE, Status.STALE, Status.STALE));
        OptionOpportunityController controller = controller(chains, scanner, ml, market, analysis, collector, context, new MarketScenarioBuilderService());

        ResponseEntity<?> response = controller.getOpportunity("2026-10-06");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("DATA_INSUFFICIENT", body.get("status"));
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertEquals("NO_TRADE", data.get("recommendation"));
        assertEquals("DATA_INSUFFICIENT", ((Map<?, ?>) data.get("scenario")).get("status"));
        assertTrue(((List<?>) data.get("dataQualityIssues")).contains("NIFTY_STALE"));
        verifyNoInteractions(chains, scanner, ml, market, analysis);
    }

    @Test
    void expiredHistoricalOptionsSnapshotReturnsDataInsufficientBeforePrediction() throws Exception {
        NiftyOptionChainService chains = mock(NiftyOptionChainService.class);
        OptionOpportunityScanner scanner = mock(OptionOpportunityScanner.class);
        MlPredictionService ml = mock(MlPredictionService.class);
        MarketDataService market = mock(MarketDataService.class);
        AnalysisService analysis = mock(AnalysisService.class);
        OptionSnapshotCollector collector = mock(OptionSnapshotCollector.class);
        NseMarketContextService context = mock(NseMarketContextService.class);
        when(context.snapshot()).thenReturn(snapshot(Status.AVAILABLE, Status.AVAILABLE, Status.AVAILABLE));
        when(chains.getNiftyOptionChain("2026-10-06")).thenReturn(mapper.readTree("""
                {"dataSource":"HISTORICAL_SNAPSHOT","snapshotTimestamp":"2026-09-30T10:00:00Z",
                 "data":[{"strike_price":22600}]}
                """));
        OptionOpportunityController controller = controller(chains, scanner, ml, market, analysis, collector, context, new MarketScenarioBuilderService());

        ResponseEntity<?> response = controller.getOpportunity("2026-10-06");

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("DATA_INSUFFICIENT", body.get("status"));
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertEquals("NO_TRADE", data.get("recommendation"));
        assertTrue(((List<?>) data.get("dataQualityIssues")).stream()
                .anyMatch(issue -> issue.toString().startsWith("OPTIONS_STALE")));
        verifyNoInteractions(scanner, ml, market, analysis);
    }

    private OptionOpportunityController controller(
            NiftyOptionChainService chains,
            OptionOpportunityScanner scanner,
            MlPredictionService ml,
            MarketDataService market,
            AnalysisService analysis,
            OptionSnapshotCollector collector,
            NseMarketContextService context,
            MarketScenarioBuilderService builder
    ) {
        return new OptionOpportunityController(chains, scanner, ml, market, analysis, collector, context, builder, 180_000);
    }

    private NseMarketContextSnapshot snapshot(Status sourceStatus, Status metricStatus, Status leadStatus) {
        Instant now = Instant.now();
        Metric return1m = new Metric(metricStatus == Status.AVAILABLE ? 0.1 : null, metricStatus);
        Metric return5m = new Metric(metricStatus == Status.AVAILABLE ? 0.12 : null, metricStatus);
        Metric return10m = new Metric(metricStatus == Status.AVAILABLE ? 0.15 : null, metricStatus);
        Metric return15m = new Metric(metricStatus == Status.AVAILABLE ? 0.18 : null, metricStatus);
        Metric lead = new Metric(leadStatus == Status.AVAILABLE ? 0.05 : null, leadStatus);

        NiftyContext nifty = new NiftyContext(
                now.toString(),
                22800.0,
                22780.0,
                22820.0,
                22770.0,
                22790.0,
                10.0,
                0.04,
                0.02,
                0.01,
                return1m,
                return5m,
                return10m,
                return15m,
                sourceStatus
        );

        FuturesContext futures = new FuturesContext(
                now.toString(),
                "NIFTY",
                "INDEX",
                "2026-10-06",
                22810.0,
                22790.0,
                22825.0,
                22780.0,
                22795.0,
                15.0,
                0.06,
                0.03,
                0.02,
                12345L,
                678L,
                5.5,
                456L,
                12345.67,
                "NIFTY",
                22800.0,
                10.0,
                return5m,
                return10m,
                return15m,
                sourceStatus
        );

        return new NseMarketContextSnapshot(now, nifty, futures, lead);
    }
}