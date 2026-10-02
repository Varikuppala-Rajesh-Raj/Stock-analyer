package com.tradingplatform.market.ml;

import com.tradingplatform.market.Candle;
import com.tradingplatform.market.indicators.FeatureVector;
import com.tradingplatform.market.indicators.TechnicalFeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts historical candles into supervised-learning examples.
 *
 * Example:
 *
 *     Historical candles
 *             ↓
 *     Feature calculation
 *             ↓
 *     Look into the future
 *             ↓
 *     Calculate future return
 *             ↓
 *     UP / DOWN / NEUTRAL
 *
 * This class is responsible for creating the training dataset.
 */
@Service
public class TrainingDatasetService {

    /**
     * TechnicalFeatureService currently requires
     * at least 60 candles.
     */
    private static final int MIN_CANDLES = 60;

    private final TechnicalFeatureService featureService;
        private final int featureWindowCandles;

        @Autowired
    public TrainingDatasetService(
                        TechnicalFeatureService featureService,
                        @Value("${trading.analysis.intraday-window-candles:500}") int featureWindowCandles) {

        this.featureService =
                featureService;
                this.featureWindowCandles = Math.max(MIN_CANDLES, featureWindowCandles);
        }

        public TrainingDatasetService(TechnicalFeatureService featureService) {
                this(featureService, 500);
    }

    /**
     * Generate supervised-learning examples.
     *
     * @param candles historical candles in chronological order
     * @param horizonCandles number of candles to look ahead
     * @param thresholdPercent movement required to classify
     *                        a candle as UP or DOWN
     *
     * @return generated training examples
     */
    public List<TrainingExample> generate(
            List<Candle> candles,
            int horizonCandles,
            BigDecimal thresholdPercent) {

        List<Candle> ordered = normalize(candles);
        validate(
                ordered,
                horizonCandles,
                thresholdPercent
        );

        List<TrainingExample> examples =
                new ArrayList<>();

        /*
         * Example:
         *
         * candles = 100
         * horizon = 3
         *
         * Last usable training candle:
         *
         * 100 - 3 - 1 = 96
         *
         * Candle 96 can look at candle 99.
         */
        int lastTrainingIndex =
                ordered.size()
                        - horizonCandles
                        - 1;

        /*
         * Start after enough candles exist
         * to calculate EMA50 and the rest of
         * the feature vector.
         */
        for (
                int i = MIN_CANDLES - 1;
                i <= lastTrainingIndex;
                i++
        ) {

            /*
             * IMPORTANT:
             *
             * Only candles up to the current candle
             * are provided to the feature service.
             *
             * Future candles are NOT included.
             *
             * This prevents future-data leakage.
             */
            List<Candle> historicalWindow =
                    ordered.subList(
                            Math.max(0, i + 1 - featureWindowCandles),
                            i + 1
                    );

            Candle current =
                    ordered.get(i);

            Candle future =
                    ordered.get(
                            i + horizonCandles
                    );

            if (!Duration.between(current.timestamp(), future.timestamp())
                    .equals(Duration.ofMinutes(5L * horizonCandles))) {
                continue;
            }

            FeatureVector features =
                    featureService.calculate(
                            historicalWindow
                    );

            BigDecimal currentClose =
                    current.close();

            BigDecimal futureClose =
                    future.close();

            /*
             * Future return:
             *
             * ((futureClose - currentClose)
             *  / currentClose) * 100
             */
            BigDecimal futureReturnPercent =
                    futureClose
                            .subtract(
                                    currentClose
                            )
                            .multiply(
                                    BigDecimal.valueOf(100)
                            )
                            .divide(
                                    currentClose,
                                    8,
                                    RoundingMode.HALF_UP
                            );

            TrainingExample.Target target =
                    classify(
                            futureReturnPercent,
                            thresholdPercent
                    );

            TrainingExample example =
                    new TrainingExample(
                            current.timestamp(),
                            features,
                            target,
                            currentClose,
                            futureClose,
                            futureReturnPercent
                    );

            examples.add(example);
        }

        return examples;
    }

    /** Dataset rows must be chronological and have exactly one candle per timestamp. */
    private List<Candle> normalize(List<Candle> candles) {
        if (candles == null) return null;
        Map<java.time.Instant, Candle> byTimestamp = new LinkedHashMap<>();
        candles.stream().sorted(Comparator.comparing(Candle::timestamp))
                .forEach(candle -> byTimestamp.put(candle.timestamp(), candle));
        return new ArrayList<>(byTimestamp.values());
    }

    /**
     * Classify future movement.
     *
     * Example threshold:
     *
     *     0.20%
     *
     * Future return:
     *
     *     +0.35% -> UP
     *     -0.41% -> DOWN
     *     +0.08% -> NEUTRAL
     */
    private TrainingExample.Target classify(
            BigDecimal futureReturnPercent,
            BigDecimal thresholdPercent) {

        /*
         * Positive movement.
         */
        if (
                futureReturnPercent.compareTo(
                        thresholdPercent
                ) >= 0
        ) {

            return TrainingExample.Target.UP;
        }

        /*
         * Negative movement.
         */
        if (
                futureReturnPercent.compareTo(
                        thresholdPercent.negate()
                ) <= 0
        ) {

            return TrainingExample.Target.DOWN;
        }

        /*
         * Movement is too small to be considered
         * a meaningful directional movement.
         */
        return TrainingExample.Target.NEUTRAL;
    }

    /**
     * Validate dataset-generation parameters.
     */
    private void validate(
            List<Candle> candles,
            int horizonCandles,
            BigDecimal thresholdPercent) {

        if (candles == null) {

            throw new IllegalArgumentException(
                    "Candles cannot be null."
            );
        }

        if (candles.size() < MIN_CANDLES) {

            throw new IllegalArgumentException(
                    "At least "
                            + MIN_CANDLES
                            + " candles are required."
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

        /*
         * We need enough candles for:
         *
         * current candle
         * +
         * future candle
         */
        int minimumRequired =
                MIN_CANDLES
                        + horizonCandles;

        if (candles.size() < minimumRequired) {

            throw new IllegalArgumentException(
                    "Not enough candles to generate "
                            + "training examples. Required at least "
                            + minimumRequired
                            + " candles."
            );
        }
    }
}
