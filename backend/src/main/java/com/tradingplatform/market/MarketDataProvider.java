package com.tradingplatform.market;
import java.time.LocalDate; import java.util.List;
public interface MarketDataProvider {
    Quote getQuote(Instrument instrument);
    List<Candle> getHistoricalCandles(String instrumentKey, Timeframe timeframe, LocalDate from, LocalDate to);
    List<Candle> getIntradayCandles(String instrumentKey, Timeframe timeframe);
}
