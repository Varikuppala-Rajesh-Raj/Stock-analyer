package com.tradingplatform.market.options;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.signal.AnalysisService;
import com.tradingplatform.signal.SignalResult;
import com.tradingplatform.signal.TechnicalAnalysisResult;
import com.tradingplatform.market.indicators.CanonicalTechnicalFeatures;
import com.tradingplatform.market.nse.NseMarketContextService;
import com.tradingplatform.market.nse.NseMarketContextSnapshot;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.Metric;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.Status;
import com.tradingplatform.market.scenario.MarketScenario;
import com.tradingplatform.market.scenario.MarketScenarioBuilderService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/options/nifty")
public class OptionOpportunityController {

    private final NiftyOptionChainService optionChainService;
    private final OptionOpportunityScanner scanner;
    private final MlPredictionService mlPredictionService;
    private final MarketDataService marketDataService;
    private final AnalysisService analysisService;
    private final OptionSnapshotCollector snapshotCollector;
        private final NseMarketContextService marketContextService;
        private final MarketScenarioBuilderService marketScenarioBuilderService;
        private final Duration dataMaxAge;

    public OptionOpportunityController(
            NiftyOptionChainService optionChainService,
            OptionOpportunityScanner scanner,
            MlPredictionService mlPredictionService,
            MarketDataService marketDataService,
            AnalysisService analysisService,
            OptionSnapshotCollector snapshotCollector,
            NseMarketContextService marketContextService,
            MarketScenarioBuilderService marketScenarioBuilderService,
            @Value("${trading.nse.market-context.stale-ms:180000}") long dataMaxAgeMs
    ) {
        this.optionChainService = optionChainService;
        this.scanner = scanner;
        this.mlPredictionService = mlPredictionService;
        this.marketDataService = marketDataService;
        this.analysisService = analysisService;
        this.snapshotCollector = snapshotCollector;
        this.marketContextService = marketContextService;
        this.marketScenarioBuilderService = marketScenarioBuilderService;
        this.dataMaxAge = Duration.ofMillis(dataMaxAgeMs);
    }

    @GetMapping("/opportunity")
    public ResponseEntity<?> getOpportunity(
            @RequestParam String expiry
    ) {

                NseMarketContextSnapshot marketContext = marketContextService.snapshot();
                List<String> qualityIssues = contextQualityIssues(marketContext);
                if (!qualityIssues.isEmpty()) {
                        return dataInsufficient(expiry, marketContext, qualityIssues, null, null);
                }

                JsonNode optionChainResponse;
                try {
                        optionChainResponse = optionChainService.getNiftyOptionChain(expiry);
                } catch (RuntimeException error) {
                        return dataInsufficient(expiry, marketContext,
                                        List.of("OPTIONS_UNAVAILABLE: " + safeMessage(error)), null, null);
                }
                JsonNode chain = optionChainResponse.path("data");
                if (!chain.isArray() || chain.isEmpty()) {
                        return dataInsufficient(expiry, marketContext,
                                        List.of("OPTIONS_UNAVAILABLE: option chain is empty."), null, null);
                }
                String optionDataSource = optionChainResponse.path("dataSource").asText("LIVE_RESPONSE");
                String optionSnapshotTimestamp = optionChainResponse.path("snapshotTimestamp").asText(null);
                if ("HISTORICAL_SNAPSHOT".equalsIgnoreCase(optionDataSource)
                                && !isFreshSnapshot(optionSnapshotTimestamp, Instant.now())) {
                        return dataInsufficient(expiry, marketContext,
                                        List.of("OPTIONS_STALE: historical option snapshot is missing a fresh timestamp."),
                                        optionDataSource, optionSnapshotTimestamp);
                }

        // ============================================================
        // 1. GET LATEST NIFTY M5 CANDLES
        // ============================================================

        List<Candle> candles =
                marketDataService.intraday(
                        "NIFTY",
                        Timeframe.M5
                );

        if (candles == null || candles.size() < 60) {
            throw new IllegalStateException(
                    "At least 60 NIFTY M5 candles are required."
            );
        }

        // ============================================================
        // 2. TECHNICAL ANALYSIS
        // ============================================================

        SignalResult signalResult =
                analysisService.analyze(
                        "NIFTY",
                        Timeframe.M5,
                        candles
                );

        TechnicalAnalysisResult technicalAnalysis =
                signalResult.technicalAnalysis();

        if (technicalAnalysis == null) {
            throw new IllegalStateException(
                    "Technical analysis result is unavailable."
            );
        }

        // ============================================================
        // 3. DIRECTION ML
        // ============================================================

        JsonNode directionResult =
                mlPredictionService.predictNifty(
                        candles,
                        15,
                        0.20,
                        technicalAnalysis
                );

        String direction =
                directionResult
                        .path("prediction")
                        .asText("NEUTRAL");

        // ============================================================
        // 4. MAGNITUDE ML
        // ============================================================

        JsonNode magnitudeResult =
                mlPredictionService.predictNiftyMagnitude(
                        15,
                        0.20,
                        technicalAnalysis
                );

        double expectedReturnPercent =
                magnitudeResult
                        .path("predictedReturnPercent")
                        .asDouble(Double.NaN);

        OptionForecastGuard.requireUsable(directionResult, expectedReturnPercent);

        JsonNode optionModelStatus = mlPredictionService.optionMagnitudeModelStatus();
        boolean optionReturnModelReady = "READY".equalsIgnoreCase(
                optionModelStatus.path("status").asText()
        );

        // ============================================================
        // ============================================================
        // 5. MARKET SCENARIO + OPTION OPPORTUNITY SCANNER
        // ============================================================

        double currentLevel = marketContext != null && marketContext.nifty() != null && marketContext.nifty().ltp() != null
                ? marketContext.nifty().ltp()
                : 0.0;

        MarketScenario scenario = marketScenarioBuilderService.build(
                directionResult,
                currentLevel,
                expectedReturnPercent,
                directionResult.path("horizonMinutes").asInt(15),
                direction
        );

        Map<String, Object> result =
                scanner.scan(chain, expiry,
                        CanonicalTechnicalFeatures.from(technicalAnalysis).entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().doubleValue())),
                        directionResult, expectedReturnPercent, optionReturnModelReady, scenario);

                result.put("optionChainDataSource", optionDataSource);
                if (optionSnapshotTimestamp != null) result.put("optionChainSnapshotTimestamp", optionSnapshotTimestamp);

        // ============================================================
        // 7. ADD ML INFORMATION
        // ============================================================

        Map<String, Object> niftyContext = new LinkedHashMap<>();
        niftyContext.put("direction", direction);
        niftyContext.put("directionProbabilities", directionResult.path("probabilities"));
        niftyContext.put("forecastHorizonMinutes", scenario.getHorizonMinutes());
        niftyContext.put("expectedReturnPercent", expectedReturnPercent);
        niftyContext.put("expectedMovePoints", scenario.getExpectedMovePoints());
        niftyContext.put("technicalSignal", signalResult.signal().name());
        niftyContext.put("technicalScore", technicalAnalysis.overallTechnicalScore());
        niftyContext.put("optionReturnModelStatus", optionModelStatus.path("status").asText("UNKNOWN"));
        niftyContext.put("status", scenario.getScenarioValidity());
        niftyContext.put("directionalOutlook", "DOWN".equals(direction)
                ? "Historical NIFTY model favours a down move; PE is the directional side to watch, not an executed trade."
                : "UP".equals(direction)
                        ? "Historical NIFTY model favours an up move; CE is the directional side to watch, not an executed trade."
                        : "Historical NIFTY model is neutral; no directional option side is preferred.");
        result.put("niftyContext", niftyContext);
        result.put("scenario", scenario.toMap());

        result.put(
                "expiry",
                expiry
        );

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "success",

                        "data",
                        result
                )
        );
    }

    @GetMapping("/collect")
    public ResponseEntity<?> collect(@RequestParam String expiry) {
        return ResponseEntity.ok(Map.of("status", "success", "snapshotsStored", snapshotCollector.collect(expiry), "expiry", expiry));
    }

        private List<String> contextQualityIssues(NseMarketContextSnapshot context) {
                List<String> issues = new java.util.ArrayList<>();
                if (context == null || context.nifty() == null) {
                        issues.add("NIFTY_UNAVAILABLE");
                        issues.add("FUTURES_UNAVAILABLE");
                        return issues;
                }
                if (context.nifty().status() != Status.AVAILABLE) issues.add("NIFTY_" + context.nifty().status());
                if (context.futures() == null || context.futures().status() != Status.AVAILABLE) {
                        issues.add("FUTURES_" + (context.futures() == null ? Status.UNAVAILABLE : context.futures().status()));
                }
                requireAvailable(issues, "NIFTY_1M", context.nifty().return1m());
                requireAvailable(issues, "NIFTY_5M", context.nifty().return5m());
                requireAvailable(issues, "NIFTY_10M", context.nifty().return10m());
                requireAvailable(issues, "NIFTY_15M", context.nifty().return15m());
                if (context.futures() != null) {
                        requireAvailable(issues, "FUTURES_5M", context.futures().return5m());
                        requireAvailable(issues, "FUTURES_10M", context.futures().return10m());
                        requireAvailable(issues, "FUTURES_15M", context.futures().return15m());
                }
                requireAvailable(issues, "FUTURES_LEAD_5M", context.futuresLead());
                return issues;
        }

        private static void requireAvailable(List<String> issues, String name, Metric metric) {
                if (metric == null || metric.status() != Status.AVAILABLE || metric.value() == null) {
                        issues.add(name + "_" + (metric == null ? Status.UNAVAILABLE : metric.status()));
                }
        }

        private boolean isFreshSnapshot(String timestamp, Instant now) {
                if (timestamp == null || timestamp.isBlank()) return false;
                try {
                        Duration age = Duration.between(Instant.parse(timestamp), now);
                        return !age.isNegative() && age.compareTo(dataMaxAge) <= 0;
                } catch (RuntimeException error) {
                        return false;
                }
        }

        private ResponseEntity<?> dataInsufficient(String expiry, NseMarketContextSnapshot context,
                                                                                           List<String> reasons, String optionsSource,
                                                                                           String optionsTimestamp) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("status", "DATA_INSUFFICIENT");
                data.put("symbol", "NIFTY");
                data.put("spot", context == null || context.nifty() == null ? null : context.nifty().ltp());
                data.put("atmStrike", null);
                data.put("recommendation", "NO_TRADE");
                data.put("recommendedOption", null);
                data.put("ceCandidates", List.of());
                data.put("peCandidates", List.of());
                data.put("decisionReason", "Opportunity withheld because required market data is stale, unavailable, or still collecting.");
                data.put("dataQualityIssues", List.copyOf(reasons));
                data.put("scenario", Map.of("status", "DATA_INSUFFICIENT"));
                data.put("niftyContext", Map.of("status", "DATA_INSUFFICIENT"));
                data.put("expiry", expiry);
                if (optionsSource != null) data.put("optionChainDataSource", optionsSource);
                if (optionsTimestamp != null) data.put("optionChainSnapshotTimestamp", optionsTimestamp);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "DATA_INSUFFICIENT");
                response.put("data", data);
                return ResponseEntity.ok(response);
        }

        private static String safeMessage(RuntimeException error) {
                return error.getMessage() == null ? "Option-chain provider request failed." : error.getMessage();
        }
}
