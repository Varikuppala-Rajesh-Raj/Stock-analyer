package com.tradingplatform.scanner;

import com.tradingplatform.signal.*; import java.math.BigDecimal; import java.util.List;
/** signalStrength is direction-aware: BUY uses bullishScore; SELL uses bearishScore. */
public record ScannerResult(String symbol, Signal signal, int bullishScore, int bearishScore, int signalStrength, BigDecimal price, BigDecimal entry, BigDecimal stopLoss, BigDecimal target, BigDecimal riskReward, List<String> reasons, List<StrategyResult> strategies) {}
