package com.tradingplatform.signal;
import java.math.BigDecimal; import java.util.List;
public record SignalResult(String symbol, Signal signal, int bullishScore, int bearishScore, int signalStrength,
                           BigDecimal entry, BigDecimal stopLoss, BigDecimal target, BigDecimal riskReward,
                           List<String> reasons, List<StrategyResult> strategies,
                           TechnicalAnalysisResult technicalAnalysis) {
 public SignalResult(String symbol, Signal signal, int bullishScore, int bearishScore, int signalStrength,
                     BigDecimal entry, BigDecimal stopLoss, BigDecimal target, BigDecimal riskReward,
                     List<String> reasons, List<StrategyResult> strategies) {
  this(symbol, signal, bullishScore, bearishScore, signalStrength, entry, stopLoss, target, riskReward, reasons, strategies, null);
 }
}
