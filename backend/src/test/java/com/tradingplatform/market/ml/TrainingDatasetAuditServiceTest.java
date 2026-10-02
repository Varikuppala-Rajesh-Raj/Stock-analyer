package com.tradingplatform.market.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.indicators.CanonicalTechnicalFeatures;
import com.tradingplatform.market.indicators.FeatureVector;
import com.tradingplatform.market.indicators.TechnicalFeatureService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrainingDatasetAuditServiceTest {

    @Test
    void auditReportsObservedSessionCoverageAndRejectsWeekendTargets() {
        TechnicalFeatureService featureService = mock(TechnicalFeatureService.class);
        when(featureService.calculate(anyList())).thenReturn(features());
        TrainingDatasetService datasetService = new TrainingDatasetService(featureService);
        List<Candle> candles = new ArrayList<>();
        appendSession(candles, LocalDate.of(2025, 1, 3));
        appendSession(candles, LocalDate.of(2025, 1, 6));
        List<TrainingExample> examples = datasetService.generate(candles, 3, new BigDecimal("0.20"));
        MarketDataService.TrainingHistory history = new MarketDataService.TrainingHistory(candles, 150, 0);

        Map<String, Object> report = new TrainingDatasetAuditService().audit(
                LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 6), history, examples,
                3, 15, new BigDecimal("0.20"));

        Map<?, ?> coverage = (Map<?, ?>) report.get("coverage");
        assertEquals(2, coverage.get("observedTradingSessions"));
        assertEquals(150, coverage.get("expectedCandlesForObservedSessions"));
        assertEquals(0, coverage.get("missingCandlesWithinObservedSessions"));
        assertEquals(85, report.get("trainingExamples"));

        Map<?, ?> alignment = (Map<?, ?>) report.get("targetAlignment");
        assertEquals(88, alignment.get("eligibleAnchorsAfterWarmupAndHorizon"));
        assertEquals(85, alignment.get("exactHorizonTargets"));
        assertEquals(3, alignment.get("missingExactTargets"));
        assertEquals(3, alignment.get("weekendCrossingMissingTargets"));
        assertEquals(0, alignment.get("longerTargetsIncorrectlyAccepted"));

        Map<?, ?> sampleCounts = (Map<?, ?>) report.get("sampleCounts");
        assertEquals(85, sampleCounts.get("total"));
        assertEquals(65, sampleCounts.get("training"));
        assertEquals(3, sampleCounts.get("purged"));
        assertEquals(17, sampleCounts.get("holdout"));
        assertEquals(Boolean.FALSE, report.get("retrainingPerformed"));
        assertEquals(Boolean.FALSE, report.get("modelGateModified"));
        assertEquals("REVIEW_REQUIRED", report.get("dataQualityStatus"));
        assertEquals("NIFTY ML DATA AUDIT", report.get("reportTitle"));
        assertEquals("DO_NOT_RETRAIN", report.get("retrainingRecommendation"));
        assertEquals(0.0, ((Map<?, ?>) report.get("absoluteReturnDistributionPercent")).get("mean"));
        Map<?, ?> sourceDiagnostics = (Map<?, ?>) report.get("featureSourceDiagnostics");
        assertEquals("UNAVAILABLE_ZERO_VOLUME", ((Map<?, ?>) sourceDiagnostics.get("volume")).get("status"));
        assertEquals("UNAVAILABLE_ZERO_DENOMINATOR", ((Map<?, ?>) sourceDiagnostics.get("vwap")).get("status"));
        assertEquals("UNAVAILABLE_NO_ASOF_CONTEXT",
            ((Map<?, ?>) sourceDiagnostics.get("historicalMarketContext")).get("status"));
        assertEquals("B: broader factors joined from point-in-time historical observations",
            ((Map<?, ?>) report.get("architectureDecision")).get("finalArchitecture"));

        Map<?, ?> targetDefinition = (Map<?, ?>) report.get("targetDefinition");
        assertEquals("T + 15 minutes exactly", targetDefinition.get("targetTimestamp"));
        assertEquals("100 * (futureClose - currentClose) / currentClose", targetDefinition.get("returnFormula"));

        Map<?, ?> baseline = (Map<?, ?>) report.get("holdoutBaselines");
        assertEquals("NEUTRAL", baseline.get("directionMajorityLabel"));
        assertEquals(1.0, baseline.get("directionMajorityAccuracy"));
        assertEquals(0.0, baseline.get("magnitudeZeroReturnMaePercent"));

        Map<?, ?> featureCompleteness = (Map<?, ?>) report.get("featureCompleteness");
        assertEquals(CanonicalTechnicalFeatures.NAMES.size(), featureCompleteness.size());
        Map<?, ?> ema9 = (Map<?, ?>) featureCompleteness.get("ema9");
        assertEquals(85, ema9.get("nonNullCount"));
        assertEquals(0, ema9.get("nullCount"));
        assertEquals(85, ema9.get("zeroCount"));
        assertEquals(Boolean.TRUE, report.get("noFutureLeakage"));

        Map<?, ?> quality = (Map<?, ?>) report.get("featureQuality");
        assertEquals(28, quality.get("featureCount"));
        assertEquals(85 * 28, quality.get("expectedValueCount"));
        assertEquals(85 * 28, quality.get("validValueCount"));
        assertEquals(100.0, quality.get("availableValuePercent"));
        assertEquals(0, quality.get("missingValueCount"));
        assertEquals(85 * 28, quality.get("zeroValueCount"));
        assertEquals(28, quality.get("constantFeatureCount"));
        assertEquals(28, quality.get("zeroDominantFeatureCount"));

        Map<?, ?> factors = (Map<?, ?>) report.get("factorCoverage");
        Map<?, ?> niftyFactors = (Map<?, ?>) factors.get("NIFTY OHLC-derived technicals");
        assertEquals(100.0, niftyFactors.get("availablePercent"));
        assertEquals(0.0, niftyFactors.get("missingPercent"));
        assertEquals(100.0, niftyFactors.get("zeroPercent"));
        assertEquals(28, niftyFactors.get("constantFeatureCount"));
        Map<?, ?> vix = (Map<?, ?>) factors.get("India VIX");
        assertEquals(Boolean.FALSE, vix.get("includedInTrainingSchema"));
        assertEquals(null, vix.get("availablePercent"));
    }

    private void appendSession(List<Candle> candles, LocalDate date) {
        ZoneId india = ZoneId.of("Asia/Kolkata");
        Instant start = date.atTime(9, 15).atZone(india).toInstant();
        for (int index = 0; index < 75; index++) {
            BigDecimal price = BigDecimal.valueOf(100);
            candles.add(new Candle(start.plusSeconds(index * 5L * 60), price, price, price, price, BigDecimal.ZERO));
        }
    }

    private FeatureVector features() {
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal price = BigDecimal.valueOf(100);
        Map<String, BigDecimal> strategyFeatures = new LinkedHashMap<>();
        CanonicalTechnicalFeatures.NAMES.forEach(name -> strategyFeatures.put(name, BigDecimal.ZERO));
        return new FeatureVector(price, price, price, price, price, price, price,
                zero, zero, zero, zero, zero, zero, zero, zero, zero, strategyFeatures);
    }
}