package com.tradingplatform.market.indicators;

import com.tradingplatform.market.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure technical-indicator calculations.
 *
 * This class does not know anything about:
 *
 * - Upstox
 * - Spring Boot
 * - databases
 * - WebSockets
 * - ML models
 *
 * It only receives candles and calculates indicators.
 */
public final class TechnicalIndicators {

    private static final MathContext MC =
            new MathContext(16, RoundingMode.HALF_UP);

    private TechnicalIndicators() {
    }

    // =========================================================
    // SMA
    // =========================================================

    public static BigDecimal sma(
            List<Candle> candles,
            int period) {

        if (candles == null
                || period <= 0
                || candles.size() < period) {

            return BigDecimal.ZERO;
        }

        int start = candles.size() - period;

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = start; i < candles.size(); i++) {
            sum = sum.add(
                    candles.get(i).close(),
                    MC
            );
        }

        return sum.divide(
                BigDecimal.valueOf(period),
                8,
                RoundingMode.HALF_UP
        );
    }

    // =========================================================
    // EMA
    // =========================================================

    public static BigDecimal ema(
            List<Candle> candles,
            int period) {

        if (candles == null
                || period <= 0
                || candles.size() < period) {

            return BigDecimal.ZERO;
        }

        BigDecimal multiplier =
                BigDecimal.valueOf(2)
                        .divide(
                                BigDecimal.valueOf(period + 1),
                                16,
                                RoundingMode.HALF_UP
                        );

        BigDecimal ema =
                candles.get(0).close();

        for (int i = 1; i < candles.size(); i++) {

            BigDecimal close =
                    candles.get(i).close();

            ema = ema.add(
                    multiplier.multiply(
                            close.subtract(ema, MC),
                            MC
                    ),
                    MC
            );
        }

        return ema.setScale(
                8,
                RoundingMode.HALF_UP
        );
    }

    /**
     * Calculates EMA values for every candle.
     *
     * The returned list has the same size as candles.
     *
     * Index i contains the EMA value using candles 0..i only.
     *
     * This is useful for historical ML feature generation.
     */
    public static List<BigDecimal> emaSeries(
            List<Candle> candles,
            int period) {

        List<BigDecimal> result =
                new ArrayList<>();

        if (candles == null
                || candles.isEmpty()
                || period <= 0) {

            return result;
        }

        BigDecimal multiplier =
                BigDecimal.valueOf(2)
                        .divide(
                                BigDecimal.valueOf(period + 1),
                                16,
                                RoundingMode.HALF_UP
                        );

        BigDecimal ema =
                candles.get(0).close();

        result.add(ema);

        for (int i = 1; i < candles.size(); i++) {

            BigDecimal close =
                    candles.get(i).close();

            ema = ema.add(
                    multiplier.multiply(
                            close.subtract(ema, MC),
                            MC
                    ),
                    MC
            );

            result.add(ema);
        }

        return result;
    }

    // =========================================================
    // RSI
    // =========================================================

    public static BigDecimal rsi(
            List<Candle> candles,
            int period) {

        if (candles == null
                || period <= 0
                || candles.size() <= period) {

            return BigDecimal.ZERO;
        }

        BigDecimal gainSum =
                BigDecimal.ZERO;

        BigDecimal lossSum =
                BigDecimal.ZERO;

        int start =
                candles.size() - period;

        for (int i = start; i < candles.size(); i++) {

            BigDecimal previousClose =
                    candles.get(i - 1).close();

            BigDecimal currentClose =
                    candles.get(i).close();

            BigDecimal change =
                    currentClose.subtract(
                            previousClose,
                            MC
                    );

            if (change.signum() > 0) {

                gainSum =
                        gainSum.add(
                                change,
                                MC
                        );

            } else {

                lossSum =
                        lossSum.add(
                                change.abs(),
                                MC
                        );
            }
        }

        BigDecimal averageGain =
                gainSum.divide(
                        BigDecimal.valueOf(period),
                        16,
                        RoundingMode.HALF_UP
                );

        BigDecimal averageLoss =
                lossSum.divide(
                        BigDecimal.valueOf(period),
                        16,
                        RoundingMode.HALF_UP
                );

        if (averageLoss.signum() == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal rs =
                averageGain.divide(
                        averageLoss,
                        16,
                        RoundingMode.HALF_UP
                );

        BigDecimal rsi =
                BigDecimal.valueOf(100)
                        .subtract(
                                BigDecimal.valueOf(100)
                                        .divide(
                                                BigDecimal.ONE.add(rs),
                                                16,
                                                RoundingMode.HALF_UP
                                        )
                        );

        return rsi.setScale(
                4,
                RoundingMode.HALF_UP
        );
    }

    // =========================================================
    // MACD
    // =========================================================

    public static BigDecimal macd(
            List<Candle> candles) {

        if (candles == null
                || candles.size() < 26) {

            return BigDecimal.ZERO;
        }

        BigDecimal ema12 =
                ema(candles, 12);

        BigDecimal ema26 =
                ema(candles, 26);

        return ema12
                .subtract(ema26, MC)
                .setScale(
                        8,
                        RoundingMode.HALF_UP
                );
    }

    /**
     * Efficient MACD signal calculation.
     *
     * Previous implementation repeatedly calculated EMA(12)
     * and EMA(26) for every historical prefix.
     *
     * This implementation calculates both EMA series once
     * and then calculates the MACD series in one pass.
     */
    public static BigDecimal macdSignal(
            List<Candle> candles) {

        if (candles == null
                || candles.size() < 34) {

            return BigDecimal.ZERO;
        }

        List<BigDecimal> ema12 =
                emaSeries(candles, 12);

        List<BigDecimal> ema26 =
                emaSeries(candles, 26);

        List<BigDecimal> macdValues =
                new ArrayList<>();

        for (int i = 25; i < candles.size(); i++) {

            BigDecimal macdValue =
                    ema12.get(i)
                            .subtract(
                                    ema26.get(i),
                                    MC
                            );

            macdValues.add(macdValue);
        }

        if (macdValues.size() < 9) {
            return BigDecimal.ZERO;
        }

        BigDecimal multiplier =
                BigDecimal.valueOf(2)
                        .divide(
                                BigDecimal.TEN,
                                16,
                                RoundingMode.HALF_UP
                        );

        BigDecimal signal =
                macdValues.get(0);

        for (int i = 1; i < macdValues.size(); i++) {

            signal =
                    signal.add(
                            multiplier.multiply(
                                    macdValues.get(i)
                                            .subtract(
                                                    signal,
                                                    MC
                                            ),
                                    MC
                            ),
                            MC
                    );
        }

        return signal.setScale(
                8,
                RoundingMode.HALF_UP
        );
    }

    public static BigDecimal macdHistogram(
            List<Candle> candles) {

        BigDecimal macd =
                macd(candles);

        BigDecimal signal =
                macdSignal(candles);

        return macd
                .subtract(signal, MC)
                .setScale(
                        8,
                        RoundingMode.HALF_UP
                );
    }

    // =========================================================
    // ATR
    // =========================================================

    public static BigDecimal atr(
            List<Candle> candles,
            int period) {

        if (candles == null
                || period <= 0
                || candles.size() <= period) {

            return BigDecimal.ZERO;
        }

        int start =
                candles.size() - period;

        BigDecimal sum =
                BigDecimal.ZERO;

        for (int i = start; i < candles.size(); i++) {

            Candle current =
                    candles.get(i);

            Candle previous =
                    candles.get(i - 1);

            BigDecimal highLow =
                    current.high()
                            .subtract(
                                    current.low(),
                                    MC
                            );

            BigDecimal highPreviousClose =
                    current.high()
                            .subtract(
                                    previous.close(),
                                    MC
                            )
                            .abs();

            BigDecimal lowPreviousClose =
                    current.low()
                            .subtract(
                                    previous.close(),
                                    MC
                            )
                            .abs();

            BigDecimal trueRange =
                    highLow
                            .max(highPreviousClose)
                            .max(lowPreviousClose);

            sum =
                    sum.add(
                            trueRange,
                            MC
                    );
        }

        return sum.divide(
                BigDecimal.valueOf(period),
                8,
                RoundingMode.HALF_UP
        );
    }

    // =========================================================
    // Average Volume
    // =========================================================

    public static BigDecimal averageVolume(
            List<Candle> candles,
            int period) {

        if (candles == null
                || period <= 0
                || candles.size() < period) {

            return BigDecimal.ZERO;
        }

        int start =
                candles.size() - period;

        BigDecimal sum =
                BigDecimal.ZERO;

        for (int i = start; i < candles.size(); i++) {

            sum =
                    sum.add(
                            candles.get(i).volume(),
                            MC
                    );
        }

        return sum.divide(
                BigDecimal.valueOf(period),
                8,
                RoundingMode.HALF_UP
        );
    }

    // =========================================================
    // Momentum
    // =========================================================

    public static BigDecimal momentum(
            List<Candle> candles,
            int period) {

        if (candles == null
                || period <= 0
                || candles.size() <= period) {

            return BigDecimal.ZERO;
        }

        BigDecimal current =
                candles.get(
                        candles.size() - 1
                ).close();

        BigDecimal previous =
                candles.get(
                        candles.size() - 1 - period
                ).close();

        if (previous.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return current
                .subtract(previous, MC)
                .multiply(
                        BigDecimal.valueOf(100),
                        MC
                )
                .divide(
                        previous,
                        8,
                        RoundingMode.HALF_UP
                );
    }

    // =========================================================
    // Volatility
    // =========================================================

    public static BigDecimal volatility(
            List<Candle> candles,
            int period) {

        if (candles == null
                || period <= 0
                || candles.size() < period) {

            return BigDecimal.ZERO;
        }

        BigDecimal average =
                sma(
                        candles,
                        period
                );

        int start =
                candles.size() - period;

        BigDecimal sumSquared =
                BigDecimal.ZERO;

        for (int i = start; i < candles.size(); i++) {

            BigDecimal difference =
                    candles.get(i)
                            .close()
                            .subtract(
                                    average,
                                    MC
                            );

            sumSquared =
                    sumSquared.add(
                            difference.multiply(
                                    difference,
                                    MC
                            ),
                            MC
                    );
        }

        BigDecimal variance =
                sumSquared.divide(
                        BigDecimal.valueOf(period),
                        16,
                        RoundingMode.HALF_UP
                );

        return BigDecimal.valueOf(
                        Math.sqrt(
                                variance.doubleValue()
                        )
                )
                .setScale(
                        8,
                        RoundingMode.HALF_UP
                );
    }
}