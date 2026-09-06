package com.tradingplatform.signal;

import com.tradingplatform.market.Candle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IndicatorEngine {

    private static final MathContext MC =
            new MathContext(16, RoundingMode.HALF_UP);

    public IndicatorSnapshot calculate(List<Candle> c) {

        if (c == null || c.size() < 60) {
            throw new IllegalArgumentException(
                    "At least 60 chronological candles are required for analysis."
            );
        }

        // ---------------------------------------------------------
        // Trend
        // ---------------------------------------------------------

        BigDecimal ema9 =
                ema(c, 9);

        BigDecimal ema20 =
                ema(c, 20);

        BigDecimal ema50 =
                ema(c, 50);

        BigDecimal ema200 =
                c.size() >= 200
                        ? ema(c, 200)
                        : null;

        // ---------------------------------------------------------
        // Momentum
        // ---------------------------------------------------------

        BigDecimal rsi =
                rsi(c, 14);

        BigDecimal ema12 =
                ema(c, 12);

        BigDecimal ema26 =
                ema(c, 26);

        BigDecimal macd =
                ema12.subtract(ema26, MC);

        BigDecimal signal =
                macdSignal(c);

        // ---------------------------------------------------------
        // Price / volatility
        // ---------------------------------------------------------

        BigDecimal vwap =
                vwap(c);

        BigDecimal sd =
                stdClose(c, 20);

        BigDecimal sma =
                smaClose(c, 20);

        BigDecimal atr =
                atr(c, 14);

        // ---------------------------------------------------------
        // Volume
        // ---------------------------------------------------------

        BigDecimal volume =
                smaVolume(c, 20);

        // ---------------------------------------------------------
        // Recent range
        // ---------------------------------------------------------

        int rangeStart =
                c.size() - 20;

        int rangeEnd =
                c.size() - 1;

        BigDecimal high =
                c.subList(rangeStart, rangeEnd)
                        .stream()
                        .map(Candle::high)
                        .max(BigDecimal::compareTo)
                        .orElseThrow();

        BigDecimal low =
                c.subList(rangeStart, rangeEnd)
                        .stream()
                        .map(Candle::low)
                        .min(BigDecimal::compareTo)
                        .orElseThrow();

        // ---------------------------------------------------------
        // Bollinger bands
        // ---------------------------------------------------------

        BigDecimal two =
                BigDecimal.valueOf(2);

        BigDecimal upperBand =
                sma.add(
                        sd.multiply(two, MC),
                        MC
                );

        BigDecimal lowerBand =
                sma.subtract(
                        sd.multiply(two, MC),
                        MC
                );

        return new IndicatorSnapshot(
                ema9,
                ema20,
                ema50,
                ema200,
                rsi,
                macd,
                signal,
                vwap,
                upperBand,
                lowerBand,
                atr,
                volume,
                high,
                low
        );
    }

    // =========================================================
    // EMA
    // =========================================================

    private BigDecimal ema(
            List<Candle> candles,
            int period) {

        BigDecimal multiplier =
                BigDecimal.valueOf(2)
                        .divide(
                                BigDecimal.valueOf(period + 1),
                                MC
                        );

        BigDecimal value =
                candles.get(0).close();

        for (Candle candle : candles) {

            value =
                    candle.close()
                            .subtract(value, MC)
                            .multiply(
                                    multiplier,
                                    MC
                            )
                            .add(
                                    value,
                                    MC
                            );
        }

        return value;
    }

    // =========================================================
    // EMA SERIES
    // =========================================================

    private List<BigDecimal> emaSeries(
            List<Candle> candles,
            int period) {

        List<BigDecimal> result =
                new ArrayList<>(candles.size());

        BigDecimal multiplier =
                BigDecimal.valueOf(2)
                        .divide(
                                BigDecimal.valueOf(period + 1),
                                MC
                        );

        BigDecimal value =
                candles.get(0).close();

        result.add(value);

        for (int i = 1; i < candles.size(); i++) {

            BigDecimal close =
                    candles.get(i).close();

            value =
                    close
                            .subtract(value, MC)
                            .multiply(
                                    multiplier,
                                    MC
                            )
                            .add(
                                    value,
                                    MC
                            );

            result.add(value);
        }

        return result;
    }

    // =========================================================
    // RSI
    // =========================================================

    private BigDecimal rsi(
            List<Candle> candles,
            int period) {

        BigDecimal gain =
                BigDecimal.ZERO;

        BigDecimal loss =
                BigDecimal.ZERO;

        int start =
                candles.size() - period;

        for (int i = start; i < candles.size(); i++) {

            BigDecimal difference =
                    candles.get(i)
                            .close()
                            .subtract(
                                    candles.get(i - 1).close(),
                                    MC
                            );

            if (difference.signum() > 0) {

                gain =
                        gain.add(
                                difference,
                                MC
                        );

            } else {

                loss =
                        loss.add(
                                difference.abs(),
                                MC
                        );
            }
        }

        if (loss.signum() == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal relativeStrength =
                gain.divide(
                        loss,
                        MC
                );

        return BigDecimal.valueOf(100)
                .subtract(
                        BigDecimal.valueOf(100)
                                .divide(
                                        BigDecimal.ONE.add(
                                                relativeStrength,
                                                MC
                                        ),
                                        MC
                                ),
                        MC
                );
    }

    // =========================================================
    // MACD SIGNAL
    // =========================================================

    private BigDecimal macdSignal(
            List<Candle> candles) {

        if (candles.size() < 34) {
            return BigDecimal.ZERO;
        }

        /*
         * Calculate EMA12 and EMA26 only once.
         */
        List<BigDecimal> ema12 =
                emaSeries(candles, 12);

        List<BigDecimal> ema26 =
                emaSeries(candles, 26);

        /*
         * Build MACD series.
         */
        List<BigDecimal> macdValues =
                new ArrayList<>();

        for (int i = 25; i < candles.size(); i++) {

            macdValues.add(
                    ema12.get(i)
                            .subtract(
                                    ema26.get(i),
                                    MC
                            )
            );
        }

        if (macdValues.size() < 9) {
            return BigDecimal.ZERO;
        }

        /*
         * 9-period EMA of MACD.
         */
        BigDecimal multiplier =
                BigDecimal.valueOf(2)
                        .divide(
                                BigDecimal.TEN,
                                MC
                        );

        BigDecimal signal =
                macdValues.get(0);

        for (int i = 1; i < macdValues.size(); i++) {

            signal =
                    macdValues.get(i)
                            .subtract(
                                    signal,
                                    MC
                            )
                            .multiply(
                                    multiplier,
                                    MC
                            )
                            .add(
                                    signal,
                                    MC
                            );
        }

        return signal;
    }

    // =========================================================
    // VWAP
    // =========================================================

    private BigDecimal vwap(
            List<Candle> candles) {

        BigDecimal numerator =
                BigDecimal.ZERO;

        BigDecimal denominator =
                BigDecimal.ZERO;

        for (Candle candle : candles) {

            BigDecimal typicalPrice =
                    candle.high()
                            .add(
                                    candle.low(),
                                    MC
                            )
                            .add(
                                    candle.close(),
                                    MC
                            )
                            .divide(
                                    BigDecimal.valueOf(3),
                                    MC
                            );

            numerator =
                    numerator.add(
                            typicalPrice.multiply(
                                    candle.volume(),
                                    MC
                            ),
                            MC
                    );

            denominator =
                    denominator.add(
                            candle.volume(),
                            MC
                    );
        }

        if (denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return numerator.divide(
                denominator,
                MC
        );
    }

    // =========================================================
    // SMA CLOSE
    // =========================================================

    private BigDecimal smaClose(
            List<Candle> candles,
            int period) {

        return candles
                .subList(
                        candles.size() - period,
                        candles.size()
                )
                .stream()
                .map(Candle::close)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                )
                .divide(
                        BigDecimal.valueOf(period),
                        MC
                );
    }

    // =========================================================
    // SMA VOLUME
    // =========================================================

    private BigDecimal smaVolume(
            List<Candle> candles,
            int period) {

        return candles
                .subList(
                        candles.size() - period,
                        candles.size()
                )
                .stream()
                .map(Candle::volume)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                )
                .divide(
                        BigDecimal.valueOf(period),
                        MC
                );
    }

    // =========================================================
    // STANDARD DEVIATION
    // =========================================================

    private BigDecimal stdClose(
            List<Candle> candles,
            int period) {

        BigDecimal mean =
                smaClose(
                        candles,
                        period
                );

        BigDecimal sum =
                BigDecimal.ZERO;

        for (Candle candle :
                candles.subList(
                        candles.size() - period,
                        candles.size()
                )) {

            BigDecimal difference =
                    candle.close()
                            .subtract(
                                    mean,
                                    MC
                            );

            sum =
                    sum.add(
                            difference.multiply(
                                    difference,
                                    MC
                            ),
                            MC
                    );
        }

        return BigDecimal.valueOf(
                        Math.sqrt(
                                sum.divide(
                                        BigDecimal.valueOf(period),
                                        MC
                                ).doubleValue()
                        )
                );
    }

    // =========================================================
    // ATR
    // =========================================================

    private BigDecimal atr(
            List<Candle> candles,
            int period) {

        BigDecimal total =
                BigDecimal.ZERO;

        int start =
                candles.size() - period;

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

            total =
                    total.add(
                            trueRange,
                            MC
                    );
        }

        return total.divide(
                BigDecimal.valueOf(period),
                MC
        );
    }
}