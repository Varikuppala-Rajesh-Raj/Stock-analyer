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
    private final String nikkei;
    private final String hangSeng;
    private final String indiaVix;
    private final String usdInr;
    private final String crude;
    private final String wti;
    private final String gold;
    private final String us10y;

    public GlobalMarketContextService(
            InstrumentCatalogService catalog,
            UpstoxMarketDataProvider upstox,
            MockMarketDataProvider mock,
            UpstoxGlobalInstrumentProperties instruments,
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
        this.giftNifty = configured(instruments.giftNifty(), giftNifty);
        this.sp500 = configured(instruments.sp500(), sp500);
        this.nasdaq = configured(instruments.usTech100(), nasdaq);
        this.dow = configured(instruments.dow(), dow);
        this.asianMarket = asianMarket;
        this.nikkei = configured(instruments.nikkei(), null);
        this.hangSeng = configured(instruments.hangSeng(), null);
        this.indiaVix = indiaVix;
        this.usdInr = configured(instruments.usdInr(), usdInr);
        this.crude = configured(instruments.crudeOil(), configured(instruments.brent(), crude));
        this.wti = configured(instruments.wti(), null);
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
                quoteReturn(sp500), quoteReturn(nasdaq), quoteReturn(dow),
                average(quoteReturn(nikkei), quoteReturn(hangSeng), quoteReturn(asianMarket)),
                vixQuote == null ? null : vixQuote.price().doubleValue(),
                quoteReturn(indiaVix), quoteReturn(usdInr),
                average(quoteReturn(crude), quoteReturn(wti)), quoteReturn(gold),
                quoteReturn(us10y));
    }

    private Double quoteReturn(String symbol) {
        Quote quote = quote(symbol);
        return quote == null ? null : GlobalMarketFeatureCalculator.returnPercent(quote.price(), quote.previousClose());
    }

    private Quote quote(String symbol) {
        if (blank(symbol)) return null;
        try {
            Instrument instrument = instrument(symbol);
            Quote quote = provider.getQuote(instrument);
            return MarketDataNormalizer.isQuoteStale(quote, quoteMaxAge) ? null : quote;
        } catch (IllegalArgumentException | MarketDataUnavailableException ignored) {
            return null;
        }
    }

    private List<Candle> candles(String symbol) {
        if (blank(symbol)) return List.of();
        try {
            Instrument instrument = instrument(symbol);
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

    private Instrument instrument(String keyOrSymbol) {
        if (keyOrSymbol.contains("|")) {
            return new Instrument(keyOrSymbol, keyOrSymbol, keyOrSymbol, "GLOBAL", "GLOBAL", "", true);
        }
        return catalog.resolveSymbol(keyOrSymbol);
    }

    private static String configured(String instrumentKey, String legacySymbol) {
        return blank(instrumentKey) ? legacySymbol : instrumentKey;
    }

    private static Double average(Double... values) {
        double total = 0;
        int count = 0;
        for (Double value : values) {
            if (value != null && Double.isFinite(value)) {
                total += value;
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }
}