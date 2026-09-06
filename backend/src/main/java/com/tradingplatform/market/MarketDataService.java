package com.tradingplatform.market;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MarketDataService {
 private static final int ANALYSIS_MINIMUM_CANDLES = 60;
 private final InstrumentCatalogService catalog; private final MarketDataProvider provider; private final MarketDataCache cache; private final CandlePersistenceService persistence;
 private final int analysisHistoryDays; private final int analysisWindowCandles; private final java.time.Duration quoteMaxAge;
 public MarketDataService(InstrumentCatalogService catalog, UpstoxMarketDataProvider upstox, MockMarketDataProvider mock,
                          MarketDataCache cache, CandlePersistenceService persistence,
                          @Value("${trading.MARKET_DATA_PROVIDER}") String selected,
                          @Value("${trading.analysis.intraday-history-days:10}") int analysisHistoryDays,
                          @Value("${trading.analysis.intraday-window-candles:500}") int analysisWindowCandles,
                          @Value("${trading.market-data.quote-max-age-seconds:300}") long quoteMaxAgeSeconds) {
  this.catalog=catalog; this.provider="upstox".equalsIgnoreCase(selected) ? upstox : mock; this.cache=cache; this.persistence=persistence;
  this.analysisHistoryDays=analysisHistoryDays; this.analysisWindowCandles=analysisWindowCandles; this.quoteMaxAge=java.time.Duration.ofSeconds(quoteMaxAgeSeconds);
 }
 public Instrument instrument(String symbol) { return catalog.resolveSymbol(symbol); }
 public Quote quote(String symbol) { Instrument i=instrument(symbol); return cache.getQuote(i.instrumentKey()).orElseGet(()->{Quote q=provider.getQuote(i); if (MarketDataNormalizer.isQuoteStale(q, quoteMaxAge)) throw new MarketDataUnavailableException("Quote for "+symbol+" is stale; data age exceeds configured maximum of "+quoteMaxAge.getSeconds()+" seconds."); cache.putQuote(i.instrumentKey(),q);return q;}); }
 public List<Candle> history(String symbol, Timeframe timeframe, LocalDate from, LocalDate to) { Instrument i=instrument(symbol);String range=from+":"+to;return cache.getCandles(i.instrumentKey(),timeframe,range).orElseGet(()->{List<Candle> persisted=normalise(persistence.find(i.instrumentKey(),timeframe,from,to));int required=timeframe==Timeframe.M5?100:1;if(persisted.size()>=required){cache.putCandles(i.instrumentKey(),timeframe,range,persisted);return persisted;}List<Candle> c=normalise(provider.getHistoricalCandles(i.instrumentKey(),timeframe,from,to));if(c.isEmpty())throw new MarketDataUnavailableException("No historical "+timeframe+" candles are available for "+symbol);persistence.upsert(i.instrumentKey(),timeframe,c);cache.putCandles(i.instrumentKey(),timeframe,range,c);return c;}); }

 /** Cache-first intraday retrieval that returns a window suitable for technical analysis. */
 public List<Candle> intraday(String symbol, Timeframe timeframe) {
  if (!timeframe.supportsIntraday()) throw new IllegalArgumentException("Timeframe is not supported for intraday: " + timeframe);
  Instrument instrument=instrument(symbol); String key=instrument.instrumentKey(); String range="intraday";
  List<Candle> cached=cache.getCandles(key,timeframe,range).map(this::normalise).orElse(List.of());
  if (cached.size() >= ANALYSIS_MINIMUM_CANDLES) return cached;

  LocalDate to=LocalDate.now(); LocalDate from=to.minusDays(analysisHistoryDays);
  String historicalRange="analysis:" + from + ":" + to;
  List<Candle> historical=cache.getCandles(key,timeframe,historicalRange).map(this::normalise).orElseGet(() -> {
    List<Candle> persisted=normalise(persistence.find(key,timeframe,from,to));
    if (persisted.size() >= ANALYSIS_MINIMUM_CANDLES) {
     cache.putCandles(key,timeframe,historicalRange,persisted);
     return persisted;
    }
    try {
     List<Candle> values=provider.getHistoricalCandles(key,timeframe,from,to);
     List<Candle> normalised=normalise(values);
     persistence.upsert(key,timeframe,normalised);
     cache.putCandles(key,timeframe,historicalRange,normalised);
     return normalised;
    } catch (MarketDataUnavailableException e) {
     return persisted;
    }
  });

  // This request occurs only while replenishing a cache miss or insufficient intraday entry.
  List<Candle> latest;
  try {
    latest = provider.getIntradayCandles(key, timeframe);
} catch (MarketDataUnavailableException e) {
    latest = List.of();
}
  List<Candle> combined=recentWindow(merge(historical,latest));
  if (combined.size() < ANALYSIS_MINIMUM_CANDLES) throw new MarketDataUnavailableException("Upstox returned only " + combined.size() + " chronological " + timeframe + " candles for " + symbol + "; at least " + ANALYSIS_MINIMUM_CANDLES + " are required for analysis.");
  persistence.upsert(key,timeframe,combined);
  cache.putCandles(key,timeframe,range,combined);
  return combined;
 }
 private List<Candle> merge(List<Candle> historical, List<Candle> latest) {
  Map<java.time.Instant,Candle> byTimestamp=new LinkedHashMap<>();
  MarketDataNormalizer.normalizeCandles(historical).forEach(candle -> byTimestamp.put(candle.timestamp(),candle));
  MarketDataNormalizer.normalizeCandles(latest).forEach(candle -> byTimestamp.put(candle.timestamp(),candle));
  return new ArrayList<>(byTimestamp.values());
 }
 private List<Candle> normalise(List<Candle> candles) {
  return MarketDataNormalizer.normalizeCandles(candles);
 }
 private List<Candle> recentWindow(List<Candle> candles) {
  List<Candle> ordered=normalise(candles); int from=Math.max(0,ordered.size()-analysisWindowCandles);
  return new ArrayList<>(ordered.subList(from,ordered.size()));
 }
}
