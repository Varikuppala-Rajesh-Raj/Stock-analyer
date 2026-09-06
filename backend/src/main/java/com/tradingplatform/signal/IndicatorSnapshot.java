package com.tradingplatform.signal;
import java.math.BigDecimal;
public record IndicatorSnapshot(BigDecimal ema9, BigDecimal ema20, BigDecimal ema50, BigDecimal ema200,
                                BigDecimal rsi14, BigDecimal macd, BigDecimal macdSignal, BigDecimal vwap,
                                BigDecimal upperBand, BigDecimal lowerBand, BigDecimal atr14,
                                BigDecimal volumeSma, BigDecimal recentHigh, BigDecimal recentLow) {}
