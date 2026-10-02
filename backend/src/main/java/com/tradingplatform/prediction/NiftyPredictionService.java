package com.tradingplatform.prediction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataNormalizer;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.MarketDataUnavailableException;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.indicators.CanonicalTechnicalFeatures;
import com.tradingplatform.market.indicators.TechnicalFeatureService;
import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.persistence.PredictionEntity;
import com.tradingplatform.persistence.PredictionRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class NiftyPredictionService {
    private final MarketDataService market;
    private final TechnicalFeatureService technicalFeatures;
    private final MlPredictionService ml;
    private final ObjectMapper json;
    private final PredictionRepository repository;
    private final StringRedisTemplate redis;
    private final String mlUrl;
    private final Duration maxCandleAge;
    private final HttpClient http = HttpClient.newHttpClient();

    public NiftyPredictionService(
            MarketDataService market,
            TechnicalFeatureService technicalFeatures,
            MlPredictionService ml,
            ObjectMapper json,
            PredictionRepository repository,
            StringRedisTemplate redis,
            @Value("${trading.ml.max-candle-age-seconds:900}") long maxCandleAgeSeconds,
            @Value("${trading.ml-service-url:http://localhost:8000}") String mlUrl
    ) {
        this.market = market;
        this.technicalFeatures = technicalFeatures;
        this.ml = ml;
        this.json = json;
        this.repository = repository;
        this.redis = redis;
        this.maxCandleAge = Duration.ofSeconds(maxCandleAgeSeconds);
        this.mlUrl = mlUrl;
    }

    public Map<String, Object> predict(int horizon, double threshold) {
        String cacheKey = "prediction:nifty:" + horizon + ":" + threshold;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return json.readValue(cached, new TypeReference<>() {});
            } catch (Exception ignored) {
                // Rebuild an unreadable cached prediction from current data.
            }
        }

        List<Candle> candles = market.intraday("NIFTY", Timeframe.M5);
        if (candles.size() < 60) {
            throw new MarketDataUnavailableException(
                    "At least 60 five-minute NIFTY candles are required for a prediction.");
        }
        Candle latest = candles.get(candles.size() - 1);
        if (MarketDataNormalizer.isCandleStale(latest, maxCandleAge)
                || latest.timestamp().isAfter(Instant.now())) {
            throw new MarketDataUnavailableException(
                    "Latest NIFTY candle is stale or future-dated; maximum allowed age is "
                            + maxCandleAge.toSeconds() + " seconds.");
        }

        Map<String, Double> features = technicalFeatures.calculateCanonicalFeatures(candles);
        if (features.size() != CanonicalTechnicalFeatures.NAMES.size()
                || features.values().stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new MarketDataUnavailableException("NIFTY technical features are incomplete or non-finite.");
        }

        JsonNode direction;
        JsonNode magnitude;
        try {
            direction = ml.predictNiftyFromFeatures(horizon, threshold, features);
            magnitude = ml.predictNiftyMagnitudeFromFeatures(horizon, threshold, features);
        } catch (IllegalStateException e) {
            throw new MarketDataUnavailableException(
                    "NIFTY ML models are unavailable or have not passed validation.", e);
        }

        Map<String, Object> probabilities = json.convertValue(
                direction.path("probabilities"), new TypeReference<>() {});
        double downProbability = probability(probabilities, "DOWN");
        double neutralProbability = probability(probabilities, "NEUTRAL");
        double upProbability = probability(probabilities, "UP");
        double probabilityTotal = downProbability + neutralProbability + upProbability;
        double predictedReturn = magnitude.path("predictedReturnPercent").asDouble(Double.NaN);
        BigDecimal niftyPrice = latest.close();
        if (!validProbability(downProbability) || !validProbability(neutralProbability)
                || !validProbability(upProbability) || Math.abs(probabilityTotal - 1.0) > 0.001
                || !Double.isFinite(predictedReturn) || niftyPrice == null || niftyPrice.signum() <= 0) {
            throw new MarketDataUnavailableException("NIFTY ML prediction failed output validation.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", "NIFTY");
        result.put("timestamp", Instant.now().toString());
        result.put("contextTimestamp", latest.timestamp().toString());
        result.put("timeframe", "M5");
        result.put("horizonMinutes", horizon);
        result.put("prediction", direction.path("prediction").asText());
        result.put("decision", direction.path("prediction").asText());
        result.put("probabilities", probabilities);
        result.put("confidence", Math.max(downProbability, Math.max(neutralProbability, upProbability)));
        result.put("predictedReturnPercent", predictedReturn);
        result.put("niftyPrice", niftyPrice);
        result.put("expectedMovePoints", niftyPrice.doubleValue() * predictedReturn / 100.0);
        result.put("modelVersion", direction.path("modelVersion").asText());
        result.put("magnitudeModelVersion", magnitude.path("modelVersion").asText());
        result.put("featureVersion", CanonicalTechnicalFeatures.VERSION);
        result.put("featureSchemaVersion", CanonicalTechnicalFeatures.VERSION);

        persist(result);
        try {
            redis.opsForValue().set(cacheKey, json.writeValueAsString(result), Duration.ofSeconds(30));
        } catch (Exception e) {
            throw new MarketDataUnavailableException("Unable to cache NIFTY prediction.", e);
        }
        return result;
    }

    public List<PredictionEntity> history() {
        return repository.findTop100BySymbolOrderByTimestampDesc("NIFTY");
    }

    public Map<String, Object> modelStatus() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(mlUrl + "/api/v1/model/status"))
                    .GET().timeout(Duration.ofSeconds(5)).build();
            return json.readValue(http.send(request, HttpResponse.BodyHandlers.ofString()).body(),
                    new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("status", "UNAVAILABLE");
        }
    }

    @SuppressWarnings("unchecked")
    private void persist(Map<String, Object> result) {
        PredictionEntity entity = new PredictionEntity();
        entity.id = UUID.randomUUID();
        entity.symbol = "NIFTY";
        entity.timestamp = instant(result.get("timestamp"), Instant.now());
        entity.contextTimestamp = instant(result.get("contextTimestamp"), entity.timestamp);
        entity.horizonMinutes = ((Number) result.getOrDefault("horizonMinutes", 15)).intValue();
        entity.decision = String.valueOf(result.getOrDefault("decision", "NO_TRADE"));
        entity.reason = String.valueOf(result.getOrDefault("reason", ""));
        entity.modelVersion = String.valueOf(result.getOrDefault("modelVersion", "untrained"));
        entity.featureVersion = (String) result.getOrDefault("featureVersion", null);
        entity.featureSchemaVersion = (String) result.getOrDefault("featureSchemaVersion", entity.featureVersion);
        entity.inputHash = (String) result.get("inputHash");
        entity.prediction = (String) result.get("prediction");
        entity.confidence = String.valueOf(result.getOrDefault("confidence", null));
        entity.niftyPrice = decimal(result.get("niftyPrice"));
        entity.expectedMovePoints = decimal(result.get("expectedMovePoints"));
        entity.predictedReturnPercent = decimal(result.get("predictedReturnPercent"));
        entity.predictedRangeLow = decimal(result.get("predictedRangeLow"));
        entity.predictedRangeHigh = decimal(result.get("predictedRangeHigh"));
        Map<String, Object> probabilities = (Map<String, Object>) result.getOrDefault("probabilities", Map.of());
        entity.upProbability = decimal(probabilities.getOrDefault("up", probabilities.get("UP")));
        entity.downProbability = decimal(probabilities.getOrDefault("down", probabilities.get("DOWN")));
        entity.sidewaysProbability = decimal(probabilities.getOrDefault(
                "sideways", probabilities.getOrDefault("NEUTRAL", probabilities.get("FLAT"))));
        entity.createdAt = Instant.now();
        repository.save(entity);
    }

    private static double probability(Map<String, Object> values, String label) {
        Object value = values.get(label);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static boolean validProbability(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static Instant instant(Object value, Instant fallback) {
        if (value == null) return fallback;
        try {
            return Instant.parse(value.toString());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}