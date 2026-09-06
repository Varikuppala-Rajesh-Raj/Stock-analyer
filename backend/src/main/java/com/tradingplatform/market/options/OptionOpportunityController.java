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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/options/nifty")
public class OptionOpportunityController {

    private final NiftyOptionChainService optionChainService;
    private final OptionOpportunityScanner scanner;
    private final MlPredictionService mlPredictionService;
    private final MarketDataService marketDataService;
    private final AnalysisService analysisService;
    private final OptionSnapshotCollector snapshotCollector;

    public OptionOpportunityController(
            NiftyOptionChainService optionChainService,
            OptionOpportunityScanner scanner,
            MlPredictionService mlPredictionService,
            MarketDataService marketDataService,
            AnalysisService analysisService,
            OptionSnapshotCollector snapshotCollector
    ) {
        this.optionChainService = optionChainService;
        this.scanner = scanner;
        this.mlPredictionService = mlPredictionService;
        this.marketDataService = marketDataService;
        this.analysisService = analysisService;
        this.snapshotCollector = snapshotCollector;
    }

    @GetMapping("/opportunity")
    public ResponseEntity<?> getOpportunity(
            @RequestParam String expiry
    ) {

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
        // 5. FETCH OPTION CHAIN
        // ============================================================

        JsonNode response =
                optionChainService.getNiftyOptionChain(expiry);

        JsonNode chain =
                response.path("data");

        if (!chain.isArray() || chain.isEmpty()) {
            throw new IllegalStateException(
                    "NIFTY option chain is empty for expiry: "
                            + expiry
            );
        }

        // ============================================================
        // 6. OPTION OPPORTUNITY SCANNER
        // ============================================================

        Map<String, Object> result =
                scanner.scan(chain, expiry,
                        CanonicalTechnicalFeatures.from(technicalAnalysis).entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().doubleValue())),
                        directionResult, expectedReturnPercent, optionReturnModelReady);

        if (response.has("dataSource")) {
            result.put("optionChainDataSource", response.path("dataSource").asText());
            result.put("optionChainSnapshotTimestamp", response.path("snapshotTimestamp").asText());
        }

        // ============================================================
        // 7. ADD ML INFORMATION
        // ============================================================

        Map<String, Object> niftyContext = new LinkedHashMap<>();
        niftyContext.put("direction", direction);
        niftyContext.put("directionProbabilities", directionResult.path("probabilities"));
        niftyContext.put("forecastHorizonMinutes", directionResult.path("horizonMinutes").asInt(15));
        niftyContext.put("expectedReturnPercent", expectedReturnPercent);
        niftyContext.put("expectedMovePoints", result.get("spot") instanceof Number n ? n.doubleValue() * expectedReturnPercent / 100 : 0);
        niftyContext.put("technicalSignal", signalResult.signal().name());
        niftyContext.put("technicalScore", technicalAnalysis.overallTechnicalScore());
        niftyContext.put("optionReturnModelStatus", optionModelStatus.path("status").asText("UNKNOWN"));
        niftyContext.put("directionalOutlook", "DOWN".equals(direction)
                ? "Historical NIFTY model favours a down move; PE is the directional side to watch, not an executed trade."
                : "UP".equals(direction)
                        ? "Historical NIFTY model favours an up move; CE is the directional side to watch, not an executed trade."
                        : "Historical NIFTY model is neutral; no directional option side is preferred.");
        result.put("niftyContext", niftyContext);

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
}
