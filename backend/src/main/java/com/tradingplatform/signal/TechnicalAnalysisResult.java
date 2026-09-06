package com.tradingplatform.signal;

import com.tradingplatform.market.Timeframe;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Strategy-derived data; scores are features, not independent trade votes. */
public record TechnicalAnalysisResult(
        String symbol, Timeframe timeframe, int overallTechnicalScore, Signal technicalSignal,
        List<StrategyResult> strategies, String marketRegime,
        Map<String, BigDecimal> features, List<String> reasons) {
}
