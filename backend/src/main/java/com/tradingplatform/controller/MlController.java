package com.tradingplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.market.ml.TrainingDatasetAuditService;
import com.tradingplatform.market.ml.TrainingDatasetService;
import com.tradingplatform.market.ml.TrainingExample;
import com.tradingplatform.market.options.OptionTrainingDatasetService;
import com.tradingplatform.signal.AnalysisService;
import com.tradingplatform.signal.TechnicalAnalysisResult;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        private final TrainingDatasetAuditService datasetAudits;
    private final OptionTrainingDatasetService optionDatasets;

    public MlController(
            MlPredictionService ml,
            MarketDataService market,
            ObjectMapper objectMapper,
            AnalysisService analysis,
            TrainingDatasetService datasets,
            TrainingDatasetAuditService datasetAudits,
            OptionTrainingDatasetService optionDatasets
    ) {
        this.ml = ml;
        this.market = market;
        this.objectMapper = objectMapper;
        this.analysis = analysis;
        this.datasets = datasets;
        this.datasetAudits = datasetAudits;
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

    @PostMapping("/audit/nifty")
    public Map<String, Object> auditNifty(
            @RequestParam(defaultValue = "365") int days,
            @RequestParam(defaultValue = "15") int horizonMinutes
    ) {
        validateTrainingRange(days, horizonMinutes);
        Timeframe timeframe = Timeframe.M5;
        LocalDate to = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);
        LocalDate from = to.minusDays(days - 1L);
        int horizonCandles = horizonMinutes / 5;
        BigDecimal threshold = new BigDecimal("0.20");
        MarketDataService.TrainingHistory history = market.fetchHistoricalTrainingData(
                NIFTY, timeframe, from, to);
        List<TrainingExample> examples = datasets.generate(
                history.candles(), horizonCandles, threshold);
        return auditReport(from, to, history, examples, horizonCandles, horizonMinutes, threshold);
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
        @RequestParam(defaultValue = "365")
        int days,
        @RequestParam(defaultValue = "15")
        int horizonMinutes
) {
    System.out.println("========== ML TRAINING START ==========");
    System.out.println("Days = " + days);

    // ----------------------------------------------------
    // Validate training period
    // ----------------------------------------------------
        validateTrainingRange(days, horizonMinutes);

    // ----------------------------------------------------
    // Training configuration
    // ----------------------------------------------------
    Timeframe timeframe = Timeframe.M5;

        LocalDate to = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);
        LocalDate from = to.minusDays(days - 1L);

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

    MarketDataService.TrainingHistory history =
            market.fetchHistoricalTrainingData(
                    NIFTY,
                    timeframe,
                    from,
                    to
            );
    List<Candle> candles = history.candles();


    if (candles.isEmpty()) {
        throw new IllegalStateException(
                "No historical candles available for NIFTY."
        );
    }

    // ----------------------------------------------------
    // Generate canonical ML training dataset
    // ----------------------------------------------------

    List<TrainingExample> examples =
            datasets.generate(
                    candles,
                    horizonCandles,
                    threshold
            );


    if (examples.isEmpty()) {
        throw new IllegalStateException(
                "No usable training examples were generated."
        );
    }

        Map<String, Object> datasetAudit = auditReport(
                        from, to, history, examples, horizonCandles, horizonMinutes, threshold);
        if ("DO_NOT_RETRAIN".equals(datasetAudit.get("retrainingRecommendation"))) {
                ObjectNode blocked = objectMapper.createObjectNode();
                blocked.put("status", "DATA_AUDIT_REQUIRED");
                blocked.put("message", "Training is blocked until unavailable or constant features are resolved.");
                blocked.set("datasetAudit", objectMapper.valueToTree(datasetAudit));
                return ResponseEntity.status(HttpStatus.CONFLICT).body(blocked);
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
response.set("datasetAudit", objectMapper.valueToTree(datasetAudit));

//    return ResponseEntity.ok(
//         objectMapper.createObjectNode()
//                 .set("direction", result)
//                 .set("magnitude", magnitudeResult)
// );
return ResponseEntity.ok(response);
}

        private Map<String, Object> auditReport(
                        LocalDate from,
                        LocalDate to,
                        MarketDataService.TrainingHistory history,
                        List<TrainingExample> examples,
                        int horizonCandles,
                        int horizonMinutes,
                        BigDecimal threshold
        ) {
                Map<String, Object> report = new LinkedHashMap<>(datasetAudits.audit(
                                from, to, history, examples, horizonCandles, horizonMinutes, threshold));
                report.put("currentModels", currentModelDiagnostics());
                return report;
        }

        private Map<String, Object> currentModelDiagnostics() {
                Map<String, Object> result = new LinkedHashMap<>();
                try {
                        JsonNode direction = ml.modelStatus();
                        result.put("direction", modelSummary(direction));
                } catch (RuntimeException error) {
                        result.put("direction", Map.of("status", "UNAVAILABLE", "message", String.valueOf(error.getMessage())));
                }
                try {
                        JsonNode magnitude = ml.magnitudeModelStatus();
                        result.put("magnitude", modelSummary(magnitude));
                } catch (RuntimeException error) {
                        result.put("magnitude", Map.of("status", "UNAVAILABLE", "message", String.valueOf(error.getMessage())));
                }
                return result;
        }

        private Map<String, Object> modelSummary(JsonNode model) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("status", model.path("status").asText("UNKNOWN"));
                summary.put("modelVersion", nullableText(model.path("modelVersion")));
                summary.put("eligibleForAutomation", model.path("eligibleForAutomation").asBoolean(false));
                summary.put("validationMetrics", objectMapper.convertValue(model.path("validationMetrics"), Object.class));
                if (model.has("confusionMatrix")) {
                        JsonNode matrix = model.path("confusionMatrix");
                        List<String> labels = new ArrayList<>();
                        for (JsonNode label : model.path("validationClassOrder")) {
                                labels.add(label.asText());
                        }
                        if (labels.isEmpty()) labels = List.of("DOWN", "NEUTRAL", "UP");
                        Map<String, Object> diagnostics = confusionMatrixMetrics(matrix, labels);
                        summary.putAll(diagnostics);
                }
                if (model.has("perClassMetrics")) {
                        summary.put("perClassMetrics", objectMapper.convertValue(model.path("perClassMetrics"), Object.class));
                }
                return summary;
        }

        private Map<String, Object> confusionMatrixMetrics(JsonNode matrix, List<String> labels) {
                Map<String, Object> metrics = new LinkedHashMap<>();
                int size = Math.min(matrix.size(), labels.size());
                if (size == 0) return metrics;
                double[][] values = new double[size][size];
                double total = 0.0;
                double trace = 0.0;
                double[] rows = new double[size];
                double[] columns = new double[size];
                for (int row = 0; row < size; row++) {
                        JsonNode rowValues = matrix.get(row);
                        for (int column = 0; column < size && column < rowValues.size(); column++) {
                                values[row][column] = rowValues.get(column).asDouble(0.0);
                                rows[row] += values[row][column];
                                columns[column] += values[row][column];
                                total += values[row][column];
                                if (row == column) trace += values[row][column];
                        }
                }
                double recallSum = 0.0;
                int supportedClasses = 0;
                for (int index = 0; index < size; index++) {
                        if (rows[index] > 0.0) {
                                recallSum += values[index][index] / rows[index];
                                supportedClasses++;
                        }
                }
                metrics.put("balancedAccuracy", supportedClasses == 0 ? null : recallSum / supportedClasses);
                double rowSquares = 0.0;
                double columnSquares = 0.0;
                for (int index = 0; index < size; index++) {
                        rowSquares += rows[index] * rows[index];
                        columnSquares += columns[index] * columns[index];
                }
                double mccDenominator = Math.sqrt((total * total - rowSquares) * (total * total - columnSquares));
                metrics.put("matthewsCorrelationCoefficient", mccDenominator == 0.0
                                ? null : (trace * total - dot(rows, columns)) / mccDenominator);

                double meaningfulCount = 0.0;
                double meaningfulCorrect = 0.0;
                double meaningfulPredictedDirection = 0.0;
                for (int actual = 0; actual < size; actual++) {
                        if (!"UP".equals(labels.get(actual)) && !"DOWN".equals(labels.get(actual))) continue;
                        meaningfulCount += rows[actual];
                        meaningfulCorrect += values[actual][actual];
                        for (int predicted = 0; predicted < size; predicted++) {
                                if ("UP".equals(labels.get(predicted)) || "DOWN".equals(labels.get(predicted))) {
                                        meaningfulPredictedDirection += values[actual][predicted];
                                }
                        }
                }
                metrics.put("meaningfulMoveSamples", (int) meaningfulCount);
                metrics.put("meaningfulMoveDirectionalAccuracy", meaningfulCount == 0.0
                                ? null : meaningfulCorrect / meaningfulCount);
                metrics.put("meaningfulMoveDetectionRate", meaningfulCount == 0.0
                                ? null : meaningfulPredictedDirection / meaningfulCount);
                return metrics;
        }

        private double dot(double[] left, double[] right) {
                double result = 0.0;
                for (int index = 0; index < left.length; index++) result += left[index] * right[index];
                return result;
        }

        private String nullableText(JsonNode value) {
                return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
        }

        private void validateTrainingRange(int days, int horizonMinutes) {
                if (days < 5) {
                        throw new IllegalArgumentException("Training period must be at least 5 days.");
                }
                if (horizonMinutes < 5 || horizonMinutes % 5 != 0) {
                        throw new IllegalArgumentException("horizonMinutes must be a multiple of 5 and at least 5.");
                }
        }


    private static MlPredictionService.TrainingRow trainingRow(TrainingExample example) {
        Map<String, Double> features = example.features().technicalStrategyFeatures().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().doubleValue()));
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
                                    entry -> entry.getKey(),
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
