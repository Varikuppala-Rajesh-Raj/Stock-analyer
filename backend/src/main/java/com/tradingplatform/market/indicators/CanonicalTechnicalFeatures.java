package com.tradingplatform.market.indicators;

import com.tradingplatform.signal.TechnicalAnalysisResult;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable, named ML schema shared by Java training-row generation and live prediction. */
public final class CanonicalTechnicalFeatures {
 public static final String VERSION = "technical-features-v1";
 public static final List<String> NAMES = List.of("ema9", "ema20", "ema50", "ema_slope_percent", "price_to_ema20_percent", "price_to_ema50_percent", "rsi14", "macd", "macd_signal", "macd_histogram", "roc10_percent", "atr", "atr_percent", "bollinger_width_percent", "recent_high", "recent_low", "breakout_strength", "relative_volume", "vwap_distance_percent", "ema20_distance_percent", "upper_band_distance_percent", "current_volume", "average_volume20", "regime_score", "market_regime_encoded", "context_available", "market_context_score", "overall_technical_score");
 private CanonicalTechnicalFeatures() {}
 public static Map<String, BigDecimal> from(TechnicalAnalysisResult analysis) {
  Map<String, BigDecimal> result = new LinkedHashMap<>();
  for (String name : NAMES) { BigDecimal value="market_regime_encoded".equals(name)?regime(analysis.marketRegime()):analysis.features().get(name); if(value==null) throw new IllegalArgumentException("Required technical feature is unavailable: "+name); result.put(name,value); }
  return Map.copyOf(result);
 }
 private static BigDecimal regime(String value) { return switch(value) {case "TRENDING_UP" -> BigDecimal.ONE; case "TRENDING_DOWN" -> BigDecimal.ONE.negate(); case "HIGH_VOLATILITY" -> BigDecimal.valueOf(2); case "LOW_VOLATILITY" -> BigDecimal.valueOf(-2); default -> BigDecimal.ZERO;}; }
}
