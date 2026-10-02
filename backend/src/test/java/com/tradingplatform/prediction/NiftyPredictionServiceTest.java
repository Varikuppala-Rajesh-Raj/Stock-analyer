package com.tradingplatform.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.MarketDataUnavailableException;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.indicators.CanonicalTechnicalFeatures;
import com.tradingplatform.market.indicators.TechnicalFeatureService;
import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.persistence.PredictionEntity;
import com.tradingplatform.persistence.PredictionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class NiftyPredictionServiceTest {
    private final MarketDataService market = mock(MarketDataService.class);
    private final TechnicalFeatureService technicalFeatures = mock(TechnicalFeatureService.class);
    private final MlPredictionService ml = mock(MlPredictionService.class);
    private final PredictionRepository repository = mock(PredictionRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> redisValues = mock(ValueOperations.class);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void combinesValidatedDirectionAndMagnitudeIntoPersistedPrediction() {
        Map<String, Double> features = validFeatures();
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.get(any())).thenReturn(null);
        when(market.intraday("NIFTY", Timeframe.M5)).thenReturn(candles());
        when(technicalFeatures.calculateCanonicalFeatures(any())).thenReturn(features);
        when(ml.predictNiftyFromFeatures(anyInt(), anyDouble(), anyMap())).thenReturn(
                JsonNodeFactory.instance.objectNode()
                        .put("prediction", "UP")
                        .put("modelVersion", "direction-v1")
                        .set("probabilities", JsonNodeFactory.instance.objectNode()
                                .put("DOWN", 0.1).put("NEUTRAL", 0.2).put("UP", 0.7)));
        when(ml.predictNiftyMagnitudeFromFeatures(anyInt(), anyDouble(), anyMap())).thenReturn(
                JsonNodeFactory.instance.objectNode()
                        .put("predictedReturnPercent", 0.2)
                        .put("modelVersion", "magnitude-v1"));

        Map<String, Object> prediction = service().predict(15, 0.2);

        assertEquals("UP", prediction.get("prediction"));
        assertEquals("direction-v1", prediction.get("modelVersion"));
        assertEquals("magnitude-v1", prediction.get("magnitudeModelVersion"));
        assertEquals(0.2, prediction.get("predictedReturnPercent"));
        assertEquals(44.0, prediction.get("expectedMovePoints"));
        verify(repository).save(any(PredictionEntity.class));
    }

    @Test
    void doesNotProducePredictionWhenModelHasNotPassedValidation() {
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.get(any())).thenReturn(null);
        when(market.intraday("NIFTY", Timeframe.M5)).thenReturn(candles());
        when(technicalFeatures.calculateCanonicalFeatures(any())).thenReturn(validFeatures());
        when(ml.predictNiftyFromFeatures(anyInt(), anyDouble(), anyMap()))
                .thenThrow(new IllegalStateException("MODEL_NOT_VALIDATED"));

        assertThrows(MarketDataUnavailableException.class, () -> service().predict(15, 0.2));
    }

        @Test
        void rejectsStaleCandlesBeforeCallingTheModels() {
                when(redis.opsForValue()).thenReturn(redisValues);
                when(redisValues.get(any())).thenReturn(null);
                when(market.intraday("NIFTY", Timeframe.M5)).thenReturn(candles(
                                Instant.now().minusSeconds(2 * 60 * 60L)));

                assertThrows(MarketDataUnavailableException.class, () -> service().predict(15, 0.2));
                verifyNoInteractions(ml);
        }

    private NiftyPredictionService service() {
        return new NiftyPredictionService(
                market, technicalFeatures, ml, json, repository, redis, 900, "http://localhost:8000");
    }

    private static List<Candle> candles() {
                return candles(Instant.now().minusSeconds(60 * 60L));
        }

        private static List<Candle> candles(Instant start) {
                BigDecimal price = BigDecimal.valueOf(22000);
        return IntStream.range(0, 60)
                .mapToObj(index -> new Candle(
                        start.plusSeconds(index * 60L), price, price, price, price, BigDecimal.ONE))
                .toList();
    }

        private static Map<String, Double> validFeatures() {
                return CanonicalTechnicalFeatures.NAMES.stream()
                                .collect(java.util.stream.Collectors.toMap(name -> name, name -> 1.0));
        }
}