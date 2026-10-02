package com.tradingplatform.market.context;

import com.tradingplatform.market.Candle;
import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.InstrumentCatalogService;
import com.tradingplatform.market.MarketDataNormalizer;
import com.tradingplatform.market.MarketDataProvider;
import com.tradingplatform.market.MarketDataUnavailableException;
import com.tradingplatform.market.Quote;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.MockMarketDataProvider;
import com.tradingplatform.market.UpstoxMarketDataProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Builds global context from configured instruments without fabricating missing values. */
@Service
public class GlobalMarketContextService {

    private final InstrumentCatalogService catalog;
    private final MarketDataProvider provider;
    private final Duration quoteMaxAge;
    private final String giftNifty;
    private final String sp500;
    private final String nasdaq;
    private final String dow;
    private final String asianMarket;
    private final String indiaVix;
    private final String usdInr;
    private final String crude;
    private final String gold;
    private final String us10y;

    public GlobalMarketContextService(
            InstrumentCatalogService catalog,
            UpstoxMarketDataProvider upstox,
            MockMarketDataProvider mock,
            @Value("${trading.MARKET_DATA_PROVIDER:mock}") String selected,
            @Value("${trading.global.quote-max-age-seconds:900}") long quoteMaxAgeSeconds,
            @Value("${trading.global.instruments.gift-nifty:}") String giftNifty,
            @Value("${trading.global.instruments.sp500:}") String sp500,
            @Value("${trading.global.instruments.nasdaq:}") String nasdaq,
            @Value("${trading.global.instruments.dow:}") String dow,
            @Value("${trading.global.instruments.asian-market:}") String asianMarket,
            @Value("${trading.global.instruments.india-vix:}") String indiaVix,
            @Value("${trading.global.instruments.usd-inr:}") String usdInr,
            @Value("${trading.global.instruments.crude:}") String crude,
            @Value("${trading.global.instruments.gold:}") String gold,
            @Value("${trading.global.instruments.us10y:}") String us10y
    ) {
        this.catalog = catalog;
        this.provider = "upstox".equalsIgnoreCase(selected) ? upstox : mock;
        this.quoteMaxAge = Duration.ofSeconds(quoteMaxAgeSeconds);
        this.giftNifty = giftNifty;
        this.sp500 = sp500;
        this.nasdaq = nasdaq;
        this.dow = dow;
        this.asianMarket = asianMarket;
        this.indiaVix = indiaVix;
        this.usdInr = usdInr;
        this.crude = crude;
        this.gold = gold;
        this.us10y = us10y;
    }

    public GlobalMarketContext snapshot(Instant timestamp) {
        Quote giftQuote = quote(giftNifty);
        List<Candle> giftCandles = candles(giftNifty);
        Quote vixQuote = quote(indiaVix);
        return new GlobalMarketContext(
                timestamp,
                GlobalMarketFeatureCalculator.returnOver(giftCandles, Duration.ofMinutes(5)),
                GlobalMarketFeatureCalculator.returnOver(giftCandles, Duration.ofMinutes(30)),
                GlobalMarketFeatureCalculator.returnPercent(giftQuote == null ? null : giftQuote.price(), giftQuote == null ? null : giftQuote.previousClose()),
                quoteReturn(sp500), quoteReturn(nasdaq), quoteReturn(dow), quoteReturn(asianMarket),
                vixQuote == null ? null : vixQuote.price().doubleValue(),
                quoteReturn(indiaVix), quoteReturn(usdInr), quoteReturn(crude), quoteReturn(gold),
                quoteReturn(us10y));
    }

    private Double quoteReturn(String symbol) {
        Quote quote = quote(symbol);
        return quote == null ? null : GlobalMarketFeatureCalculator.returnPercent(quote.price(), quote.previousClose());
    }

    private Quote quote(String symbol) {
        if (blank(symbol)) return null;
        try {
            Instrument instrument = catalog.resolveSymbol(symbol);
            Quote quote = provider.getQuote(instrument);
            return MarketDataNormalizer.isQuoteStale(quote, quoteMaxAge) ? null : quote;
        } catch (IllegalArgumentException | MarketDataUnavailableException ignored) {
            return null;
        }
    }

    private List<Candle> candles(String symbol) {
        if (blank(symbol)) return List.of();
        try {
            Instrument instrument = catalog.resolveSymbol(symbol);
            List<Candle> values = MarketDataNormalizer.normalizeCandles(provider.getIntradayCandles(instrument.instrumentKey(), Timeframe.M5));
            if (values.isEmpty() || MarketDataNormalizer.isCandleStale(values.get(values.size() - 1), quoteMaxAge)) return List.of();
            return values;
        } catch (IllegalArgumentException | MarketDataUnavailableException ignored) {
            return List.of();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}