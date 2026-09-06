package com.tradingplatform.market.indicators;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Technical features calculated from historical OHLCV candles.
 *
 * These features are later supplied to the ML prediction model.
 *
 * The feature vector represents only information that was available
 * at the time the prediction was made.
 */
public record FeatureVector(

        // Current price
        BigDecimal close,

        // -------------------------------------------------
        // Trend
        // -------------------------------------------------

        BigDecimal sma20,
        BigDecimal sma50,

        BigDecimal ema9,
        BigDecimal ema20,
        BigDecimal ema50,

        // -------------------------------------------------
        // Momentum
        // -------------------------------------------------

        BigDecimal rsi14,

        BigDecimal macd,
        BigDecimal macdSignal,
        BigDecimal macdHistogram,

        // -------------------------------------------------
        // Volatility
        // -------------------------------------------------

        BigDecimal atr14,

        // -------------------------------------------------
        // Price movement
        // -------------------------------------------------

        BigDecimal momentum,

        // -------------------------------------------------
        // Volume
        // -------------------------------------------------

        BigDecimal volume,
        BigDecimal averageVolume20,
        BigDecimal volumeRatio,

        // -------------------------------------------------
        // Price volatility
        // -------------------------------------------------

        BigDecimal volatility,

        // Structured strategy features for Java-side ML dataset consumers.
        Map<String, BigDecimal> technicalStrategyFeatures

) {
}
