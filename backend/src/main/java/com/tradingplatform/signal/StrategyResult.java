package com.tradingplatform.signal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** A bounded directional score and the raw values used to derive it. */
public record StrategyResult(String strategyName, Direction direction, int score, List<String> reasons,
                             Map<String, BigDecimal> features) {
 public StrategyResult(String strategyName, Direction direction, int score, List<String> reasons) {
  this(strategyName, direction, score, reasons, Map.of());
 }
}
