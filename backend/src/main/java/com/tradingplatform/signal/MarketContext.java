package com.tradingplatform.signal;
import com.tradingplatform.market.*; import java.math.BigDecimal; import java.util.List;
public record MarketContext(String symbol, Timeframe timeframe, List<Candle> candles, IndicatorSnapshot indicators, IndexContext indexContext) {
 public MarketContext(String symbol, Timeframe timeframe, List<Candle> candles, IndicatorSnapshot indicators) { this(symbol, timeframe, candles, indicators, null); }
 public BigDecimal price() { return candles.get(candles.size()-1).close(); }
 public record IndexContext(String symbol, int score, String reason) {}
}
