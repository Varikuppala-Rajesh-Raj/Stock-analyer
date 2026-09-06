package com.tradingplatform.market.ml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.context.FeatureEngine;
import com.tradingplatform.market.context.MarketContext;
import com.tradingplatform.market.context.UnifiedFeatureSchema;
import com.tradingplatform.market.context.UnifiedFeatureVector;
import com.tradingplatform.signal.TechnicalAnalysisResult;
import com.tradingplatform.market.indicators.CanonicalTechnicalFeatures;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class MlPredictionService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MlPredictionService(
            ObjectMapper objectMapper,
            @Value("${trading.ml-service-url:http://localhost:8000}")
            String mlServiceUrl
    ) {

        this.objectMapper = objectMapper;

        this.restClient =
                RestClient.builder()
                        .baseUrl(mlServiceUrl)
                        .requestFactory(
                                new SimpleClientHttpRequestFactory()
                        )
                        .build();
    }

    // ============================================================
    // DIRECTION ML TRAINING
    // ============================================================

    public JsonNode trainNifty(
            List<Candle> candles,
            int horizonCandles,
            int horizonMinutes,
            BigDecimal thresholdPercent,
            List<TrainingRow> trainingRows
    ) {

        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Candles cannot be empty."
            );
        }

        if (trainingRows == null || trainingRows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Training rows cannot be empty."
            );
        }

        if (horizonCandles <= 0) {
            throw new IllegalArgumentException(
                    "Horizon candles must be greater than zero."
            );
        }

        if (
                thresholdPercent == null
                        || thresholdPercent.signum() <= 0
        ) {
            throw new IllegalArgumentException(
                    "Threshold percent must be greater than zero."
            );
        }

        System.out.println(
                "=========================================="
        );

        System.out.println(
                "DIRECTION ML TRAINING"
        );

        System.out.println(
                "Candles: " + candles.size()
        );

        System.out.println(
                "Training rows: " + trainingRows.size()
        );

        System.out.println(
                "=========================================="
        );

        MlTrainingRequest request =
                new MlTrainingRequest(
                        "NIFTY",
                        horizonMinutes,
                        thresholdPercent.doubleValue(),
                        candles,
                        Map.of(),
                        CanonicalTechnicalFeatures.VERSION,
                        trainingRows
                );

        try {

            String json =
                    objectMapper.writeValueAsString(request);

            System.out.println(
                    "Direction training JSON length: "
                            + json.length()
            );

            return restClient
                    .post()
                    .uri("/api/v1/nifty/train")
                    .contentType(
                            MediaType.APPLICATION_JSON
                    )
                    .body(
                            json.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    )
                    .retrieve()
                    .body(JsonNode.class);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "Failed to serialize direction ML training request.",
                    e
            );

        } catch (RestClientResponseException e) {

            System.err.println(
                    "DIRECTION ML TRAINING ERROR"
            );

            System.err.println(
                    "HTTP status: "
                            + e.getStatusCode()
            );

            System.err.println(
                    "Response: "
                            + e.getResponseBodyAsString()
            );

            throw new IllegalStateException(
                    "Python direction ML service rejected training request: "
                            + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    // ============================================================
    // DIRECTION ML PREDICTION
    // ============================================================

    public JsonNode predictNifty(
            List<Candle> candles,
            int horizonMinutes,
            double thresholdPercent
    ) {

        return predictNifty(
                candles,
                horizonMinutes,
                thresholdPercent,
                Map.of()
        );
    }

    public JsonNode predictNifty(
            List<Candle> candles,
            int horizonMinutes,
            double thresholdPercent,
            TechnicalAnalysisResult technicalAnalysis
    ) {

        Map<String, Double> features =
                technicalAnalysis == null
                        ? Map.of()
                        : CanonicalTechnicalFeatures
                                .from(technicalAnalysis)
                                .entrySet()
                                .stream()
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                Map.Entry::getKey,
                                                entry -> entry.getValue().doubleValue()
                                        )
                                );

        return predictNifty(
                candles,
                horizonMinutes,
                thresholdPercent,
                features
        );
    }

    public JsonNode predictNiftyFromFeatures(
            int horizonMinutes,
            double thresholdPercent,
            Map<String, Double> technicalFeatures
    ) {
        return predictNifty(
                null,
                horizonMinutes,
                thresholdPercent,
                technicalFeatures
        );
    }

        /** Sends prepared context features to the separately versioned context endpoint. */
        public JsonNode predictNiftyFromContext(
                        int horizonMinutes,
                        double thresholdPercent,
                        UnifiedFeatureVector featureVector
        ) {
                if (featureVector == null || featureVector.features().isEmpty()) {
                        throw new IllegalArgumentException("Context features cannot be empty.");
                }
                if (!UnifiedFeatureSchema.VERSION.equals(featureVector.schemaVersion())) {
                        throw new IllegalArgumentException("Unsupported unified feature schema: " + featureVector.schemaVersion());
                }

                ContextPredictionRequest request = new ContextPredictionRequest(
                                "NIFTY", horizonMinutes, thresholdPercent, featureVector.timestamp(),
                                featureVector.schemaVersion(), featureVector.features());
                try {
                        String json = objectMapper.writeValueAsString(request);
                        return restClient.post()
                                        .uri("/api/v1/nifty/context/predict")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(json.getBytes(StandardCharsets.UTF_8))
                                        .retrieve()
                                        .body(JsonNode.class);
                } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Failed to serialize context ML prediction request.", e);
                } catch (RestClientResponseException e) {
                        throw new IllegalStateException("Python context ML service rejected prediction request: "
                                        + e.getResponseBodyAsString(), e);
                }
        }

        public JsonNode predictNiftyFromContext(
                        int horizonMinutes,
                        double thresholdPercent,
                        MarketContext context
        ) {
                return predictNiftyFromContext(horizonMinutes, thresholdPercent, FeatureEngine.flatten(context));
        }

    private JsonNode predictNifty(
            List<Candle> candles,
            int horizonMinutes,
            double thresholdPercent,
            Map<String, Double> technicalFeatures
    ) {

        if (
                technicalFeatures == null
                        || technicalFeatures.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Canonical technical features cannot be empty."
            );
        }

        MlPredictionRequest request =
                new MlPredictionRequest(
                        "NIFTY",
                        horizonMinutes,
                        thresholdPercent,
                        candles,
                        Map.of(),
                        CanonicalTechnicalFeatures.VERSION,
                        technicalFeatures
                );

        try {

            String json =
                    objectMapper.writeValueAsString(request);

            return restClient
                    .post()
                    .uri("/api/v1/nifty/predict")
                    .contentType(
                            MediaType.APPLICATION_JSON
                    )
                    .body(
                            json.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    )
                    .retrieve()
                    .body(JsonNode.class);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "Failed to serialize direction ML prediction request.",
                    e
            );

        } catch (RestClientResponseException e) {

            System.err.println(
                    "DIRECTION ML PREDICTION ERROR"
            );

            System.err.println(
                    "HTTP status: "
                            + e.getStatusCode()
            );

            System.err.println(
                    "Response: "
                            + e.getResponseBodyAsString()
            );

            throw new IllegalStateException(
                    "Python direction ML service rejected prediction request: "
                            + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    // ============================================================
    // MAGNITUDE ML TRAINING
    // ============================================================

    public JsonNode trainNiftyMagnitude(
            int horizonMinutes,
            List<MagnitudeTrainingRow> trainingRows
    ) {

        if (trainingRows == null || trainingRows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Magnitude training rows cannot be empty."
            );
        }

        if (horizonMinutes < 5) {
            throw new IllegalArgumentException(
                    "Horizon must be at least 5 minutes."
            );
        }

        System.out.println(
                "=========================================="
        );

        System.out.println(
                "NIFTY MAGNITUDE ML TRAINING"
        );

        System.out.println(
                "Training rows: "
                        + trainingRows.size()
        );

        System.out.println(
                "Horizon minutes: "
                        + horizonMinutes
        );

        System.out.println(
                "Calling Python magnitude model..."
        );

        System.out.println(
                "=========================================="
        );

        MagnitudeTrainingRequest request =
                new MagnitudeTrainingRequest(
                        "NIFTY",
                        horizonMinutes,
                        CanonicalTechnicalFeatures.VERSION,
                        trainingRows
                );

        try {

            String json =
                    objectMapper.writeValueAsString(request);

            System.out.println(
                    "Magnitude training JSON length: "
                            + json.length()
            );

            return restClient
                    .post()
                    .uri(
                            "/api/v1/nifty/magnitude/train"
                    )
                    .contentType(
                            MediaType.APPLICATION_JSON
                    )
                    .body(
                            json.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    )
                    .retrieve()
                    .body(JsonNode.class);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "Failed to serialize magnitude training request.",
                    e
            );

        } catch (RestClientResponseException e) {

            System.err.println(
                    "MAGNITUDE ML TRAINING ERROR"
            );

            System.err.println(
                    "HTTP status: "
                            + e.getStatusCode()
            );

            System.err.println(
                    "Response: "
                            + e.getResponseBodyAsString()
            );

            throw new IllegalStateException(
                    "Python magnitude ML service rejected training request: "
                            + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    // ============================================================
    // MAGNITUDE ML PREDICTION
    // ============================================================

   public JsonNode predictNiftyMagnitude(
        int horizonMinutes,
        double movementThresholdPercent,
        TechnicalAnalysisResult technicalAnalysis
) {

    if (technicalAnalysis == null) {
        throw new IllegalArgumentException(
                "Technical analysis cannot be null."
        );
    }

    Map<String, Double> features =
            CanonicalTechnicalFeatures
                    .from(technicalAnalysis)
                    .entrySet()
                    .stream()
                    .collect(
                            java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().doubleValue()
                            )
                    );

    MagnitudePredictionRequest request =
            new MagnitudePredictionRequest(
                    "NIFTY",
                    horizonMinutes,
                    movementThresholdPercent,
                    CanonicalTechnicalFeatures.VERSION,
                    features
            );

    try {

        String json =
                objectMapper.writeValueAsString(request);

        System.out.println(
                "=========================================="
        );

        System.out.println(
                "MAGNITUDE PREDICTION REQUEST"
        );

        System.out.println(
                "Horizon minutes: "
                        + horizonMinutes
        );

        System.out.println(
                "Movement threshold: "
                        + movementThresholdPercent
        );

        System.out.println(
                "Feature schema: "
                        + CanonicalTechnicalFeatures.VERSION
        );

        System.out.println(
                "Magnitude prediction JSON length: "
                        + json.length()
        );

        System.out.println(
                "Calling Python magnitude ML service..."
        );

        return restClient
                .post()
                .uri(
                        "/api/v1/nifty/magnitude/predict"
                )
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .body(
                        json.getBytes(
                                StandardCharsets.UTF_8
                        )
                )
                .retrieve()
                .body(JsonNode.class);

    } catch (JsonProcessingException e) {

        throw new IllegalStateException(
                "Failed to serialize magnitude prediction request.",
                e
        );

    } catch (RestClientResponseException e) {

        System.err.println(
                "=========================================="
        );

        System.err.println(
                "MAGNITUDE ML PREDICTION ERROR"
        );

        System.err.println(
                "HTTP status: "
                        + e.getStatusCode()
        );

        System.err.println(
                "Response: "
                        + e.getResponseBodyAsString()
        );

        System.err.println(
                "=========================================="
        );

        throw new IllegalStateException(
                "Python magnitude ML service rejected prediction request: "
                        + e.getResponseBodyAsString(),
                e
        );
    }
}

    public JsonNode predictNiftyMagnitudeFromFeatures(
            int horizonMinutes,
            double movementThresholdPercent,
            Map<String, Double> technicalFeatures
    ) {
        if (technicalFeatures == null || technicalFeatures.isEmpty()) {
            throw new IllegalArgumentException(
                    "Canonical technical features cannot be empty."
            );
        }

        MagnitudePredictionRequest request =
                new MagnitudePredictionRequest(
                        "NIFTY",
                        horizonMinutes,
                        movementThresholdPercent,
                        CanonicalTechnicalFeatures.VERSION,
                        technicalFeatures
                );

        try {
            return restClient
                    .post()
                    .uri("/api/v1/nifty/magnitude/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsBytes(request))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize magnitude prediction request.", e
            );
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Python magnitude ML service rejected prediction request: "
                            + e.getResponseBodyAsString(), e
            );
        }
    }
    // ============================================================
    // MODEL STATUS
    // ============================================================

    public JsonNode modelStatus() {

        try {

            return restClient
                    .get()
                    .uri(
                            "/api/v1/model/status"
                    )
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientResponseException e) {

            throw new IllegalStateException(
                    "Unable to retrieve Python direction ML model status.",
                    e
            );
        }
    }

    public JsonNode magnitudeModelStatus() {

    try {

        return restClient
                .get()
                .uri(
                        "/api/v1/nifty/magnitude/status"
                )
                .retrieve()
                .body(JsonNode.class);

    } catch (RestClientResponseException e) {

        throw new IllegalStateException(
                "Unable to retrieve Python magnitude ML model status.",
            e
        );
    }
    }

    public JsonNode predictNiftyOptionMagnitude(
        int horizonMinutes,
        Map<String, Double> optionFeatures
) {

    if (optionFeatures == null || optionFeatures.isEmpty()) {
        throw new IllegalArgumentException(
                "Option features cannot be empty."
        );
    }

    OptionMagnitudePredictionRequest request =
            new OptionMagnitudePredictionRequest(
                    "NIFTY",
                    horizonMinutes,
                    com.tradingplatform.market.options.OptionFeatureSchema.VERSION,
                    optionFeatures
            );

    try {

        String json =
                objectMapper.writeValueAsString(request);

        System.out.println(
                "=========================================="
        );

        System.out.println(
                "OPTION MAGNITUDE ML REQUEST"
        );

        System.out.println(
                "Horizon minutes: " + horizonMinutes
        );

        System.out.println(
                "Feature schema: "
                        + com.tradingplatform.market.options.OptionFeatureSchema.VERSION
        );

        System.out.println(
                "Feature count: "
                        + optionFeatures.size()
        );

        System.out.println(
                "Feature names: "
                        + optionFeatures.keySet()
        );

        System.out.println(
                "JSON length: " + json.length()
        );

        System.out.println(
                "Calling Python option magnitude ML service..."
        );
        System.out.println("OPTION ML REQUEST JSON:");
System.out.println(json);
        JsonNode response =
                restClient
                        .post()
                        .uri(
                                "/api/v1/nifty/options/magnitude/predict"
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(
                                json.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        )
                        .retrieve()
                        .body(JsonNode.class);

        System.out.println(
                "OPTION MAGNITUDE ML RESPONSE:"
        );

        System.out.println(response);

        System.out.println(
                "=========================================="
        );

        return response;

    } catch (JsonProcessingException e) {

        System.err.println(
                "OPTION MAGNITUDE SERIALIZATION ERROR"
        );

        e.printStackTrace();

        throw new IllegalStateException(
                "Failed to serialize option magnitude prediction request.",
                e
        );

    } catch (RestClientResponseException e) {

        System.err.println(
                "=========================================="
        );

        System.err.println(
                "OPTION MAGNITUDE ML ERROR"
        );

        System.err.println(
                "HTTP status: " + e.getStatusCode()
        );

        System.err.println(
                "Response: " + e.getResponseBodyAsString()
        );

        System.err.println(
                "=========================================="
        );

        throw new IllegalStateException(
                "Python option magnitude ML service rejected prediction request: "
                        + e.getResponseBodyAsString(),
                e
        );

    } catch (Exception e) {

        System.err.println(
                "OPTION MAGNITUDE UNEXPECTED ERROR"
        );

        e.printStackTrace();

        throw new IllegalStateException(
                "Unexpected option magnitude ML error.",
                e
        );
    }
}

    public JsonNode optionMagnitudeModelStatus() {
        try { return restClient.get().uri("/api/v1/nifty/options/magnitude/status").retrieve().body(JsonNode.class); }
        catch (RestClientResponseException exception) { throw new IllegalStateException("Unable to retrieve Python option magnitude model status.", exception); }
    }

    public JsonNode trainNiftyOptionMagnitude(int horizonMinutes, List<OptionMagnitudeTrainingRow> rows) {
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("Option magnitude training rows cannot be empty.");
        OptionMagnitudeTrainingRequest request = new OptionMagnitudeTrainingRequest("NIFTY", horizonMinutes, com.tradingplatform.market.options.OptionFeatureSchema.VERSION, rows);
        try { return restClient.post().uri("/api/v1/nifty/options/magnitude/train").contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsBytes(request)).retrieve().body(JsonNode.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Failed to serialize option magnitude training request.", e); }
        catch (RestClientResponseException e) { throw new IllegalStateException("Python option magnitude ML service rejected training request: " + e.getResponseBodyAsString(), e); }
    }
    // ============================================================
    // DIRECTION TRAINING REQUEST
    // ============================================================

    public record MlTrainingRequest(

            String symbol,

            int horizon_minutes,

            double movement_threshold_percent,

            List<Candle> candles,

            Map<String, Double> option_features,

            String feature_schema_version,

            List<TrainingRow> training_rows

    ) {
    }

    public record TrainingRow(
            String timestamp,
            Map<String, Double> features,
            String target
    ) {
    }

    // ============================================================
    // MAGNITUDE TRAINING REQUEST
    // ============================================================

    public record MagnitudeTrainingRequest(

            String symbol,

            int horizon_minutes,

            String feature_schema_version,

            List<MagnitudeTrainingRow> training_rows

    ) {
    }

    public record MagnitudeTrainingRow(

            String timestamp,

            Map<String, Double> features,

            // Must match Python MagnitudeTrainingRow.target_return_percent.
            // This is the supervised target, not a feature value.
            double target_return_percent

    ) {
    }

    // ============================================================
    // DIRECTION PREDICTION REQUEST
    // ============================================================

    public record MlPredictionRequest(

            String symbol,

            int horizon_minutes,

            double movement_threshold_percent,

            List<Candle> candles,

            Map<String, Double> option_features,

            String feature_schema_version,

            Map<String, Double> technical_features

    ) {
    }

    public record ContextPredictionRequest(
            String symbol,
            int horizon_minutes,
            double movement_threshold_percent,
            java.time.Instant timestamp,
            String feature_schema_version,
            Map<String, Double> context_features
    ) {
    }

    // ============================================================
    // MAGNITUDE PREDICTION REQUEST
    // ============================================================

    public record MagnitudePredictionRequest(

        String symbol,

        int horizon_minutes,

        double movement_threshold_percent,

        String feature_schema_version,

        Map<String, Double> technical_features

) {
}

    public record OptionMagnitudePredictionRequest(String symbol, int horizon_minutes, String feature_schema_version, Map<String, Double> option_features) { }
    public record OptionMagnitudeTrainingRequest(String symbol, int horizon_minutes, String feature_schema_version, List<OptionMagnitudeTrainingRow> training_rows) { }
    public record OptionMagnitudeTrainingRow(String timestamp, Map<String, Double> features, double target) { }


        // ============================================================
// OPTION MAGNITUDE V2 - LOG RETURN
// ============================================================

        public JsonNode trainNiftyOptionMagnitudeV2(
        int horizonMinutes,
        List<OptionMagnitudeTrainingRow> rows
) {

    if (rows == null || rows.isEmpty()) {
        throw new IllegalArgumentException(
                "Option magnitude V2 training rows cannot be empty."
        );
    }

    OptionMagnitudeTrainingRequest request =
            new OptionMagnitudeTrainingRequest(
                    "NIFTY",
                    horizonMinutes,
                    com.tradingplatform.market.options.OptionFeatureSchema.VERSION,
                    rows
            );

    try {

        String json =
                objectMapper.writeValueAsString(request);

        System.out.println(
                "=========================================="
        );

        System.out.println(
                "OPTION MAGNITUDE V2 TRAINING"
        );

        System.out.println(
                "Horizon minutes: " + horizonMinutes
        );

        System.out.println(
                "Training rows: " + rows.size()
        );

        System.out.println(
                "Feature schema: "
                        + com.tradingplatform.market.options.OptionFeatureSchema.VERSION
        );

        System.out.println(
                "JSON length: " + json.length()
        );

        System.out.println(
                "Calling Python V2 option magnitude ML service..."
        );

        return restClient
                .post()
                .uri(
                        "/api/v2/nifty/options/magnitude/train"
                )
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .body(
                        json.getBytes(
                                StandardCharsets.UTF_8
                        )
                )
                .retrieve()
                .body(JsonNode.class);

    } catch (JsonProcessingException e) {

        throw new IllegalStateException(
                "Failed to serialize option magnitude V2 training request.",
                e
        );

    } catch (RestClientResponseException e) {

        System.err.println(
                "OPTION MAGNITUDE V2 TRAINING ERROR"
        );

        System.err.println(
                "HTTP status: " + e.getStatusCode()
        );

        System.err.println(
                "Response: " + e.getResponseBodyAsString()
        );

        throw new IllegalStateException(
                "Python option magnitude V2 service rejected training request: "
                        + e.getResponseBodyAsString(),
                e
        );
    }
}


        public JsonNode optionMagnitudeV2ModelStatus() {

    try {

        return restClient
                .get()
                .uri(
                        "/api/v2/nifty/options/magnitude/status"
                )
                .retrieve()
                .body(JsonNode.class);

    } catch (RestClientResponseException e) {

        throw new IllegalStateException(
                "Unable to retrieve Python option magnitude V2 model status.",
                e
        );
    }
}


        public JsonNode predictNiftyOptionMagnitudeV2(
        int horizonMinutes,
        Map<String, Double> optionFeatures
) {

    if (
            optionFeatures == null
                    || optionFeatures.isEmpty()
    ) {
        throw new IllegalArgumentException(
                "Option features cannot be empty."
        );
    }

    OptionMagnitudePredictionRequest request =
            new OptionMagnitudePredictionRequest(
                    "NIFTY",
                    horizonMinutes,
                    com.tradingplatform.market.options.OptionFeatureSchema.VERSION,
                    optionFeatures
            );

    try {

        return restClient
                .post()
                .uri(
                        "/api/v2/nifty/options/magnitude/predict"
                )
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .body(
                        objectMapper.writeValueAsBytes(request)
                )
                .retrieve()
                .body(JsonNode.class);

    } catch (JsonProcessingException e) {

        throw new IllegalStateException(
                "Failed to serialize option magnitude V2 prediction request.",
                e
        );

    } catch (RestClientResponseException e) {

        throw new IllegalStateException(
                "Python option magnitude V2 service rejected prediction request: "
                        + e.getResponseBodyAsString(),
                e
        );
    }
}

public JsonNode diagnoseNiftyOptionMagnitude(
        int horizonMinutes,
        List<OptionMagnitudeTrainingRow> rows
) {

    if (rows == null || rows.isEmpty()) {
        throw new IllegalArgumentException(
                "Option magnitude diagnostic rows cannot be empty."
        );
    }

    OptionMagnitudeTrainingRequest request =
            new OptionMagnitudeTrainingRequest(
                    "NIFTY",
                    horizonMinutes,
                    com.tradingplatform.market.options.OptionFeatureSchema.VERSION,
                    rows
            );

    try {

        return restClient
                .post()
                .uri(
                        "/api/v1/nifty/options/magnitude/diagnostics"
                )
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .body(
                        objectMapper.writeValueAsBytes(request)
                )
                .retrieve()
                .body(JsonNode.class);

    } catch (JsonProcessingException e) {

        throw new IllegalStateException(
                "Failed to serialize option magnitude diagnostic request.",
                e
        );

    } catch (RestClientResponseException e) {

        throw new IllegalStateException(
                "Python option magnitude diagnostic service rejected request: "
                        + e.getResponseBodyAsString(),
                e
        );
    }
}






}
