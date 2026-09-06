package com.tradingplatform.market;
import java.time.LocalDate; import java.util.List; import org.springframework.stereotype.Component;
/** Test-only provider: it never fabricates data for an application response. */
@Component
public class MockMarketDataProvider implements MarketDataProvider {
 public Quote getQuote(Instrument instrument) { throw new MarketDataUnavailableException("Mock provider has no configured data; live market data is unavailable."); }
 public List<Candle> getHistoricalCandles(String key, Timeframe timeframe, LocalDate from, LocalDate to) { throw new MarketDataUnavailableException("Mock provider has no configured data; live market data is unavailable."); }
 public List<Candle> getIntradayCandles(String key, Timeframe timeframe) { throw new MarketDataUnavailableException("Mock provider has no configured data; live market data is unavailable."); }
}
