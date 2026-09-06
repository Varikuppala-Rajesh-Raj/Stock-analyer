package com.tradingplatform.market.ml;

import com.tradingplatform.market.indicators.FeatureVector;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents one supervised-learning training example.
 *
 * At prediction time:
 *
 *     timestamp
 *          +
 *     FeatureVector
 *
 * are available.
 *
 * The target is what actually happened after the
 * configured prediction horizon.
 */
public record TrainingExample(

        /**
         * Timestamp of the candle at which the prediction
         * would have been made.
         */
        Instant timestamp,

        /**
         * Technical features available at prediction time.
         */
        FeatureVector features,

        /**
         * Future market direction.
         */
        Target target,

        /**
         * Price at prediction time.
         */
        BigDecimal currentClose,

        /**
         * Price after the prediction horizon.
         */
        BigDecimal futureClose,

        /**
         * Percentage price movement between currentClose
         * and futureClose.
         */
        BigDecimal futureReturnPercent

) {

    /**
     * Classification target for the ML model.
     */
    public enum Target {

        /**
         * Future price moved above the positive threshold.
         */
        UP,

        /**
         * Future price moved below the negative threshold.
         */
        DOWN,

        /**
         * Future movement remained inside the threshold.
         */
        NEUTRAL
    }
}