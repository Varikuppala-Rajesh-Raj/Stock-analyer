package com.tradingplatform.decision;
import com.tradingplatform.signal.Signal; import java.math.BigDecimal; import java.time.Instant; import java.util.*;
/** Quantitative recommendation only. It never submits an order. */
public record Decision(UUID signalId,String symbol,Signal direction,Instant timestamp,int opportunityScore,String quality,BigDecimal confidence,BigDecimal entryPrice,BigDecimal entryMin,BigDecimal entryMax,BigDecimal stopLoss,BigDecimal target1,BigDecimal target2,BigDecimal riskReward,int suggestedQuantity,BigDecimal capitalRequired,MarketRegime regime,String entryCondition,String invalidationCondition,List<String> reasons,List<String> risks) {}
