package com.tradingplatform.market.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.indicators.CanonicalTechnicalFeatures;
import com.tradingplatform.market.indicators.TechnicalFeatureService;
import com.tradingplatform.persistence.CandleEntity;
import com.tradingplatform.persistence.CandleRepository;
import com.tradingplatform.signal.MarketContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class HistoricalNiftyPredictionService {

    private final CandleRepository candles;
    private final MlPredictionService ml;
    private final TechnicalFeatureService technicalFeatures;

    public HistoricalNiftyPredictionService(
            CandleRepository candles,
            MlPredictionService ml,
            TechnicalFeatureService technicalFeatures
    ) {
        this.candles = candles;
        this.ml = ml;
        this.technicalFeatures = technicalFeatures;
    }

    /**
     * Generates the NIFTY prediction available at a historical
     * option snapshot timestamp.
     *
     * IMPORTANT:
     *
     * Only candles <= snapshot timestamp are used.
     *
     * No future candles are used to calculate the features.
     */
    public NiftyContext predictAt(
            Instant timestamp,
            int horizonMinutes
    ) {

        // ============================================================
        // 1. LOAD HISTORICAL NIFTY CANDLES
        // ============================================================

        List<CandleEntity> entities =
                candles.findByInstrumentKeyAndTimeframeAndTimestampLessThanEqualOrderByTimestampAsc(
        "NSE_INDEX|Nifty 50",
        Timeframe.M5.name(),
        timestamp
);
        if (entities == null || entities.size() < 60) {

            throw new IllegalStateException(
                    "Not enough historical NIFTY candles at "
                            + timestamp
                            + ". Required=60, available="
                            + (entities == null ? 0 : entities.size())
            );
        }

        // ============================================================
        // 2. CONVERT ENTITY -> CANDLE
        // ============================================================

        List<Candle> historicalCandles =
                entities.stream()
                        .map(this::toCandle)
                        .toList();

        Candle latest =
                historicalCandles.get(
                        historicalCandles.size() - 1
                );

        // ============================================================
        // 3. SAFETY CHECK
        // ============================================================

        if (latest.timestamp().isAfter(timestamp)) {

            throw new IllegalStateException(
                    "Historical NIFTY candle is after requested "
                            + "option snapshot timestamp. "
                            + "candle="
                            + latest.timestamp()
                            + ", snapshot="
                            + timestamp
            );
        }

        // ============================================================
        // 4. CALCULATE POINT-IN-TIME TECHNICAL FEATURES
        // ============================================================

        /*
         * TechnicalFeatureService only receives candles up to
         * the current historical timestamp.
         *
         * Therefore no future candle can leak into the features.
         */
        Map<String, Double> features =
        new LinkedHashMap<>(
                technicalFeatures.calculateCanonicalFeatures(
                        historicalCandles
                )
        );

        // ============================================================
        // 5. VALIDATE TECHNICAL FEATURES
        // ============================================================

        if (features.isEmpty()) {

            throw new IllegalStateException(
                    "Historical NIFTY technical features are empty "
                            + "at " + timestamp
            );
        }

        if (features.values()
                .stream()
                .anyMatch(
                        value ->
                                value == null
                                        || !Double.isFinite(value)
                )) {

            throw new IllegalStateException(
                    "Historical NIFTY technical features contain "
                            + "non-finite values at "
                            + timestamp
            );
        }

        // ============================================================
        // 6. NIFTY DIRECTION PREDICTION
        // ============================================================

        JsonNode direction =
                ml.predictNiftyFromFeatures(
                        horizonMinutes,
                        0.1,
                        features
                );

        // ============================================================
        // 7. NIFTY MAGNITUDE PREDICTION
        // ============================================================

        JsonNode magnitude =
                ml.predictNiftyMagnitudeFromFeatures(
                        horizonMinutes,
                        0.1,
                        features
                );

        // ============================================================
        // 8. EXTRACT DIRECTION PROBABILITIES
        // ============================================================

        double downProbability =
                direction
                        .path("probabilities")
                        .path("DOWN")
                        .asDouble(Double.NaN);

        double neutralProbability =
                direction
                        .path("probabilities")
                        .path("NEUTRAL")
                        .asDouble(Double.NaN);

        double upProbability =
                direction
                        .path("probabilities")
                        .path("UP")
                        .asDouble(Double.NaN);

        // ============================================================
        // 9. EXTRACT NIFTY EXPECTED RETURN
        // ============================================================

        double predictedReturnPercent =
                magnitude
                        .path("predictedReturnPercent")
                        .asDouble(Double.NaN);

        // ============================================================
        // 10. EXPECTED MOVE IN NIFTY POINTS
        // ============================================================

        double spot =
                latest.close() == null
                        ? Double.NaN
                        : latest.close().doubleValue();

        double expectedMovePoints =
                spot * predictedReturnPercent / 100.0;

        // ============================================================
        // 11. FINAL VALIDATION
        // ============================================================

        if (!Double.isFinite(downProbability)
                || !Double.isFinite(neutralProbability)
                || !Double.isFinite(upProbability)
                || !Double.isFinite(predictedReturnPercent)
                || !Double.isFinite(expectedMovePoints)) {

            throw new IllegalStateException(
                    "Historical NIFTY prediction contains "
                            + "non-finite values at "
                            + timestamp
            );
        }

        // ============================================================
        // 12. RETURN HISTORICAL NIFTY CONTEXT
        // ============================================================

        return new NiftyContext(
                downProbability,
                neutralProbability,
                upProbability,
                predictedReturnPercent,
                expectedMovePoints
        );
    }

    // ================================================================
    // ENTITY -> CANDLE
    // ================================================================
    


private Candle toCandle(
            CandleEntity entity
    ) {

        return new Candle(
                entity.timestamp,
                entity.open,
                entity.high,
                entity.low,
                entity.close,
                entity.volume
        );
    }

    // ================================================================
    // RESULT
    // ================================================================

    public record NiftyContext(

            double downProbability,

            double neutralProbability,

            double upProbability,

            double predictedReturnPercent,

            double expectedMovePoints

    ) {
    }
}