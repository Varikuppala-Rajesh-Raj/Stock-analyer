package com.tradingplatform.market.indicators;

import com.tradingplatform.market.Candle;
import com.tradingplatform.signal.IndicatorEngine;
import com.tradingplatform.signal.MarketContext;
import com.tradingplatform.signal.TechnicalStrategyEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Converts historical candles into a machine-learning feature vector.
 *
 * Important:
 *
 * Only candles supplied to this method are used.
 *
 * This is important when generating training data because
 * future candles must never leak into the feature calculation.
 */
@Service
public class TechnicalFeatureService {

    /**
     * Minimum number of candles required.
     *
     * EMA50 is the longest indicator currently used.
     */
    private static final int MIN_CANDLES = 60;

    private final IndicatorEngine indicatorEngine;
    private final TechnicalStrategyEngine technicalStrategies;

    public TechnicalFeatureService(
            IndicatorEngine indicatorEngine,
            TechnicalStrategyEngine technicalStrategies) {

        this.indicatorEngine = indicatorEngine;
        this.technicalStrategies = technicalStrategies;
    }

    /**
     * Calculate the complete feature vector using the
     * latest candle in the supplied historical window.
     *
     * IMPORTANT:
     *
     * Only the supplied historical window is used.
     * No future candle can enter the feature calculation.
     */
    public FeatureVector calculate(
            List<Candle> candles) {

        validate(candles);

        Candle latest =
                candles.get(
                        candles.size() - 1
                );

        BigDecimal close =
                latest.close();

        // =====================================================
        // TREND
        // =====================================================

        BigDecimal sma20 =
                TechnicalIndicators.sma(
                        candles,
                        20
                );

        BigDecimal sma50 =
                TechnicalIndicators.sma(
                        candles,
                        50
                );

        BigDecimal ema9 =
                TechnicalIndicators.ema(
                        candles,
                        9
                );

        BigDecimal ema20 =
                TechnicalIndicators.ema(
                        candles,
                        20
                );

        BigDecimal ema50 =
                TechnicalIndicators.ema(
                        candles,
                        50
                );

        // =====================================================
        // MOMENTUM
        // =====================================================

        BigDecimal rsi14 =
                TechnicalIndicators.rsi(
                        candles,
                        14
                );

        BigDecimal macd =
                TechnicalIndicators.macd(
                        candles
                );

        BigDecimal macdSignal =
                TechnicalIndicators.macdSignal(
                        candles
                );

        BigDecimal macdHistogram =
                TechnicalIndicators.macdHistogram(
                        candles
                );

        // =====================================================
        // VOLATILITY
        // =====================================================

        BigDecimal atr14 =
                TechnicalIndicators.atr(
                        candles,
                        14
                );

        BigDecimal volatility =
                TechnicalIndicators.volatility(
                        candles,
                        20
                );

        // =====================================================
        // PRICE MOVEMENT
        // =====================================================

        BigDecimal momentum =
                TechnicalIndicators.momentum(
                        candles,
                        10
                );

        // =====================================================
        // VOLUME
        // =====================================================

        BigDecimal volume =
                latest.volume();

        BigDecimal averageVolume20 =
                TechnicalIndicators.averageVolume(
                        candles,
                        20
                );

        BigDecimal volumeRatio =
                calculateVolumeRatio(
                        volume,
                        averageVolume20
                );

        // =====================================================
        // TECHNICAL STRATEGY ANALYSIS
        // =====================================================

        /*
         * Build the point-in-time market context.
         *
         * indicatorEngine.calculate(candles)
         * only receives candles available at this point.
         */
        Map<String, BigDecimal> canonicalFeatures =
                CanonicalTechnicalFeatures.from(
                        technicalStrategies.analyze(
                                new MarketContext(
                                        "FEATURE_WINDOW",
                                        null,
                                        candles,
                                        indicatorEngine.calculate(candles)
                                )
                        )
                );

        // =====================================================
        // FINAL FEATURE VECTOR
        // =====================================================

        return new FeatureVector(
                close,

                // -------------------------------------------------
                // Trend
                // -------------------------------------------------
                sma20,
                sma50,
                ema9,
                ema20,
                ema50,

                // -------------------------------------------------
                // Momentum
                // -------------------------------------------------
                rsi14,
                macd,
                macdSignal,
                macdHistogram,

                // -------------------------------------------------
                // Volatility
                // -------------------------------------------------
                atr14,

                // -------------------------------------------------
                // Price movement
                // -------------------------------------------------
                momentum,

                // -------------------------------------------------
                // Volume
                // -------------------------------------------------
                volume,
                averageVolume20,
                volumeRatio,

                // -------------------------------------------------
                // Price volatility
                // -------------------------------------------------
                volatility,

                // -------------------------------------------------
                // Canonical technical ML features
                // -------------------------------------------------
                canonicalFeatures
        );
    }

    /**
     * Calculate:
     *
     * current volume / average volume
     */

public Map<String, Double> calculateCanonicalFeatures(
        List<Candle> candles
) {
    validate(candles);

    return CanonicalTechnicalFeatures.from(
            technicalStrategies.analyze(
                    new MarketContext(
                            "FEATURE_WINDOW",
                            null,
                            candles,
                            indicatorEngine.calculate(candles)
                    )
            )
    ).entrySet()
            .stream()
            .collect(
                    java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().doubleValue(),
                            (a, b) -> b,
                            java.util.LinkedHashMap::new
                    )
            );
}


    private BigDecimal calculateVolumeRatio(
            BigDecimal volume,
            BigDecimal averageVolume) {

        if (volume == null
                || averageVolume == null
                || averageVolume.signum() == 0) {

            return BigDecimal.ZERO;
        }

        return volume.divide(
                averageVolume,
                8,
                RoundingMode.HALF_UP
        );
    }

    /**
     * Validate input candles.
     */
    private void validate(
            List<Candle> candles) {

        if (candles == null) {

            throw new IllegalArgumentException(
                    "Candles cannot be null."
            );
        }

        if (candles.size() < MIN_CANDLES) {

            throw new IllegalArgumentException(
                    "At least "
                            + MIN_CANDLES
                            + " candles are required to calculate "
                            + "technical features."
            );
        }
    }
}