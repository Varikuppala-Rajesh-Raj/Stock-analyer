package com.tradingplatform.signal;
import com.tradingplatform.market.*; import java.util.*; import org.springframework.stereotype.Service;
@Service public class AnalysisService {
 private final MarketDataService market; private final IndicatorEngine indicators; private final SignalEngine signals;
 public AnalysisService(MarketDataService market, IndicatorEngine indicators, SignalEngine signals) {this.market=market;this.indicators=indicators;this.signals=signals;}
 public SignalResult analyze(String symbol, Timeframe timeframe) { List<Candle> candles=timeframe.supportsIntraday()?market.intraday(symbol,timeframe):market.history(symbol,timeframe,java.time.LocalDate.now().minusYears(2),java.time.LocalDate.now()); return analyze(symbol, timeframe, candles); }
 /** Lets callers that already fetched cached candles avoid a second market-data lookup. */
 public SignalResult analyze(String symbol, Timeframe timeframe, List<Candle> candles) { return signals.evaluate(new MarketContext(symbol,timeframe,candles,indicators.calculate(candles))); }
}
