package com.tradingplatform.signal;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Converts the structured technical state into the legacy signal/risk response. */
@Component
public class SignalEngine {
 private final TechnicalStrategyEngine technicalStrategies; private final BigDecimal stopMultiplier; private final BigDecimal rewardMultiple;
 public SignalEngine(TechnicalStrategyEngine technicalStrategies,
                     @Value("${trading.signal.atr-stop-multiplier:1.5}") BigDecimal stopMultiplier,
                     @Value("${trading.signal.reward-multiple:2}") BigDecimal rewardMultiple) {
  this.technicalStrategies=technicalStrategies; this.stopMultiplier=stopMultiplier; this.rewardMultiple=rewardMultiple;
 }
 public SignalResult evaluate(MarketContext context) {
  TechnicalAnalysisResult technical=technicalStrategies.analyze(context);
  int bullish=bounded(50 + technical.overallTechnicalScore() / 2), bearish=100-bullish;
  Signal signal=technical.technicalSignal(); int strength=signal==Signal.SELL?bearish:bullish;
  BigDecimal entry=context.price(), risk=context.indicators().atr14().multiply(stopMultiplier);
  if (risk.signum() <= 0 || signal == Signal.HOLD) return new SignalResult(context.symbol(),signal,bullish,bearish,strength,null,null,null,null,technical.reasons(),technical.strategies(),technical);
  boolean buy=signal==Signal.BUY; BigDecimal stop=buy?entry.subtract(risk):entry.add(risk);
  BigDecimal target=buy?entry.add(risk.multiply(rewardMultiple)):entry.subtract(risk.multiply(rewardMultiple));
  return new SignalResult(context.symbol(),signal,bullish,bearish,strength,entry,stop,target,rewardMultiple,technical.reasons(),technical.strategies(),technical);
 }
 private static int bounded(int value) { return Math.max(0,Math.min(100,value)); }
}
