package com.tradingplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.market.ml.TrainingDatasetService;
import com.tradingplatform.market.ml.TrainingExample;
import com.tradingplatform.market.options.OptionTrainingDatasetService;
import com.tradingplatform.signal.AnalysisService;
import com.tradingplatform.signal.TechnicalAnalysisResult;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.Console;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Test endpoints for the Java -> Python ML pipeline.
 *
 * These endpoints are intentionally simple.
 * Later the Decision Engine will call the ML service
 * internally instead of exposing everything directly.
 */
@RestController
@RequestMapping("/api/ml")
public class MlController {

    /*
     * IMPORTANT:
     *
     * This is the application symbol.
     *
     * InstrumentCatalogService resolves:
     *
     * NIFTY
     *    ↓
     * NSE_INDEX|Nifty 50
     *
     * Do NOT put the Upstox instrument key here.
     */
    private static final String NIFTY = "NIFTY";

    private final MlPredictionService ml;
    private final MarketDataService market;
    private final ObjectMapper objectMapper;
    private final AnalysisService analysis;
    private final TrainingDatasetService datasets;
    private final OptionTrainingDatasetService optionDatasets;

    public MlController(
            MlPredictionService ml,
            MarketDataService market,
            ObjectMapper objectMapper,
            AnalysisService analysis,
            TrainingDatasetService datasets, OptionTrainingDatasetService optionDatasets
    ) {
        this.ml = ml;
        this.market = market;
        this.objectMapper = objectMapper;
        this.analysis = analysis;
        this.datasets = datasets;
        this.optionDatasets = optionDatasets;
    }

    /**
     * Check whether the Python model is trained.
     */
    @GetMapping("/status")
    public JsonNode status() {
        return ml.modelStatus();
    }

    @GetMapping("/magnitude/status")
    public JsonNode magnitudeStatus() { return ml.magnitudeModelStatus(); }

    @GetMapping("/options/magnitude/status")
    public JsonNode optionMagnitudeStatus() { return ml.optionMagnitudeModelStatus(); }

    @PostMapping("/options/magnitude/train")
    public JsonNode trainOptionMagnitude(@RequestParam(defaultValue = "15") int horizonMinutes) {
        return ml.trainNiftyOptionMagnitude(horizonMinutes, optionDatasets.generate(horizonMinutes));
    }

    /**
     * Train NIFTY model using historical M5 candles.
     *
     * Example:
     *
     * POST /api/ml/train/nifty?days=30
     *
     * M5 × 3 candles = 15 minute prediction horizon.
     */
   @PostMapping("/train/nifty")
public ResponseEntity<JsonNode> trainNifty(
        @RequestParam(defaultValue = "30")
        int days,
        @RequestParam(defaultValue = "15")
        int horizonMinutes
) {
    System.out.println("========== ML TRAINING START ==========");
    System.out.println("Days = " + days);

    Console console = System.console();
    if (console != null) {
        console.printf("Training NIFTY model for %d days...\n", days);
    }

    // ----------------------------------------------------
    // Validate training period
    // ----------------------------------------------------
    if (days < 5) {
        throw new IllegalArgumentException(
                "Training period must be at least 5 days."
        );
    }

    if (horizonMinutes < 5 || horizonMinutes % 5 != 0) {
        throw new IllegalArgumentException(
                "horizonMinutes must be a multiple of 5 and at least 5."
        );
    }

    // ----------------------------------------------------
    // Training configuration
    // ----------------------------------------------------
    Timeframe timeframe = Timeframe.M5;

    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(days);

    int horizonCandles = horizonMinutes / 5;

    BigDecimal threshold =
            new BigDecimal("0.20");

    // ----------------------------------------------------
    // Fetch historical candles
    // ----------------------------------------------------
    System.out.println(
            "Fetching historical candles from "
                    + from
                    + " to "
                    + to
                    + "..."
    );

    List<Candle> candles =
            market.history(
                    NIFTY,
                    timeframe,
                    from,
                    to
            );

    System.out.println(
            "Fetched "
                    + candles.size()
                    + " candles."
    );

    if (candles.isEmpty()) {
        throw new IllegalStateException(
                "No historical candles available for NIFTY."
        );
    }

    // ----------------------------------------------------
    // Generate canonical ML training dataset
    // ----------------------------------------------------
    System.out.println(
            "Generating ML training dataset..."
    );

    List<TrainingExample> examples =
            datasets.generate(
                    candles,
                    horizonCandles,
                    threshold
            );

    System.out.println(
            "Generated "
                    + examples.size()
                    + " training examples."
    );

    if (examples.isEmpty()) {
        throw new IllegalStateException(
                "No usable training examples were generated."
        );
    }

    // ----------------------------------------------------
    // Convert Java training examples
    // into rows expected by Python
    // ----------------------------------------------------
    System.out.println(
            "Converting training examples to ML rows..."
    );

    List<MlPredictionService.TrainingRow> trainingRows =
            examples.stream()
                    .map(MlController::trainingRow)
                    .toList();
        List<MlPredictionService.MagnitudeTrainingRow> magnitudeRows =
        examples.stream()
                .map(MlController::magnitudeTrainingRow)
                .toList();

    System.out.println(
            "Converted "
                    + trainingRows.size()
                    + " training rows."
    );

    // ----------------------------------------------------
    // Send dataset to Python ML service
    // ----------------------------------------------------
    System.out.println(
            "Training NIFTY model with:"
    );

    System.out.println(
            "Horizon candles = "
                    + horizonCandles
    );

    System.out.println(
            "Horizon minutes = "
                    + horizonMinutes
    );

    System.out.println(
            "Threshold = "
                    + threshold
    );

    System.out.println(
            "Calling Python ML service..."
    );

    JsonNode result =
            ml.trainNifty(
                    candles,
                    horizonCandles,
                    horizonMinutes,
                    threshold,
                    trainingRows
            );

    // ----------------------------------------------------
    // Training completed
    // ----------------------------------------------------
    System.out.println(
            "Python ML training completed."
    );

    System.out.println(
        "Calling Python magnitude ML service..."
);

JsonNode magnitudeResult =
        ml.trainNiftyMagnitude(
                horizonMinutes,
                magnitudeRows
        );

System.out.println(
        "Magnitude ML training completed."
);


    System.out.println(
            "========== ML TRAINING END =========="
    );
    ObjectNode response =
        objectMapper.createObjectNode();

response.set("direction", result);
response.set("magnitude", magnitudeResult);

//    return ResponseEntity.ok(
//         objectMapper.createObjectNode()
//                 .set("direction", result)
//                 .set("magnitude", magnitudeResult)
// );
return ResponseEntity.ok(response);
}


    private static MlPredictionService.TrainingRow trainingRow(TrainingExample example) {
        Map<String, Double> features = example.features().technicalStrategyFeatures().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().doubleValue()));
        return new MlPredictionService.TrainingRow(example.timestamp().toString(), features, example.target().name());
    }



    private static MlPredictionService.MagnitudeTrainingRow magnitudeTrainingRow(
        TrainingExample example
) {

    Map<String, Double> features =
            example.features()
                    .technicalStrategyFeatures()
                    .entrySet()
                    .stream()
                    .collect(
                            java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().doubleValue()
                            )
                    );

    return new MlPredictionService.MagnitudeTrainingRow(
            example.timestamp().toString(),
            features,
            example.futureReturnPercent().doubleValue()
    );
}
    /**
     * Predict the next 15-minute NIFTY movement.
     */
    @GetMapping("/predict/nifty")
    public ResponseEntity<JsonNode> predictNifty(
            @RequestParam(defaultValue = "15") int horizonMinutes
    ) {
        JsonNode status = ml.modelStatus();

if (!"READY".equalsIgnoreCase(status.path("status").asText())) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
        objectMapper.createObjectNode()
            .put("status", "MODEL_NOT_TRAINED")
            .put("message", "Train the NIFTY model before requesting predictions.")
    );
}
        Timeframe timeframe = Timeframe.M5;

        LocalDate to = LocalDate.now();
        LocalDate from =
                to.minusDays(10);

        List<Candle> candles =
                market.history(
                        NIFTY,
                        timeframe,
                        from,
                        to
                );

        JsonNode result =
                ml.predictNifty(
                        candles,
                        horizonMinutes,
                        0.20,
                        analysis.analyze(NIFTY, timeframe, candles).technicalAnalysis()
                );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/magnitude/predict/nifty")
public ResponseEntity<JsonNode> predictNiftyMagnitude() {

    System.out.println("==========================================");
    System.out.println("NIFTY MAGNITUDE PREDICTION");
    System.out.println("==========================================");

    Timeframe timeframe = Timeframe.M5;

    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(10);

    System.out.println(
            "Fetching candles from "
                    + from
                    + " to "
                    + to
                    + "..."
    );

    List<Candle> candles =
            market.history(
                    NIFTY,
                    timeframe,
                    from,
                    to
            );

    System.out.println(
            "Fetched "
                    + candles.size()
                    + " candles."
    );

    if (candles.size() < 60) {
        throw new IllegalStateException(
                "Not enough candles for magnitude prediction."
        );
    }

    // ----------------------------------------------------
    // Calculate technical analysis
    // ----------------------------------------------------

    TechnicalAnalysisResult technicalAnalysis =
            analysis
                    .analyze(
                            NIFTY,
                            timeframe,
                            candles
                    )
                    .technicalAnalysis();

    System.out.println(
            "Technical analysis calculated."
    );

    // ----------------------------------------------------
    // Call magnitude ML
    // ----------------------------------------------------

    System.out.println(
            "Calling Python magnitude ML service..."
    );

    JsonNode result =
            ml.predictNiftyMagnitude(
                    15,
                    0.20,
                    technicalAnalysis
            );

    System.out.println(
            "Magnitude prediction received."
    );

    System.out.println(
            "=========================================="
    );

    return ResponseEntity.ok(result);
}

// ============================================================
// OPTION MAGNITUDE V2
// ============================================================

@GetMapping("/v2/options/magnitude/status")
public JsonNode optionMagnitudeV2Status() {

    return ml.optionMagnitudeV2ModelStatus();
}


@PostMapping("/v2/options/magnitude/train")
public JsonNode trainOptionMagnitudeV2(
        @RequestParam(defaultValue = "15")
        int horizonMinutes
) {

    System.out.println(
            "=========================================="
    );

    System.out.println(
            "OPTION MAGNITUDE V2 TRAINING"
    );

    System.out.println(
            "Horizon minutes: "
                    + horizonMinutes
    );

    List<MlPredictionService.OptionMagnitudeTrainingRow> rows =
            optionDatasets.generate(
                    horizonMinutes
            );

    System.out.println(
            "Generated option training rows: "
                    + rows.size()
    );

    return ml.trainNiftyOptionMagnitudeV2(
            horizonMinutes,
            rows
    );
}

@PostMapping("/v2/options/magnitude/predict")
public JsonNode predictOptionMagnitudeV2(
        @RequestParam(defaultValue = "15")
        int horizonMinutes,
        @RequestBody Map<String, Double> optionFeatures
) {
    return ml.predictNiftyOptionMagnitudeV2(
            horizonMinutes,
            optionFeatures
    );
}


@PostMapping("/options/magnitude/diagnostics")
public JsonNode diagnoseOptionMagnitude(
        @RequestParam(defaultValue = "15")
        int horizonMinutes
) {

    System.out.println(
            "=========================================="
    );

    System.out.println(
            "OPTION MAGNITUDE V1 DIAGNOSTICS"
    );

    System.out.println(
            "Horizon minutes: "
                    + horizonMinutes
    );

    List<MlPredictionService.OptionMagnitudeTrainingRow> rows =
            optionDatasets.generate(
                    horizonMinutes
            );

    System.out.println(
            "Generated diagnostic rows: "
                    + rows.size()
    );

    return ml.diagnoseNiftyOptionMagnitude(
            horizonMinutes,
            rows
    );
}


}
