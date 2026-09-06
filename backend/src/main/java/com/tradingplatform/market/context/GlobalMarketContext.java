package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global market measurements used as NIFTY prediction context.
 *
 * Values are normally returns/changes rather than absolute prices.
 */
public record GlobalMarketContext(

        Instant timestamp,

        // GIFT NIFTY
        Double giftNiftyReturn5m,
        Double giftNiftyReturn30m,
        Double giftNiftyOvernightReturn,

        // US
        Double sp500Return,
        Double nasdaqReturn,
        Double dowReturn,

        // Asia
        Double asianMarketReturn,

        // India
        Double indiaVix,
        Double indiaVixChange,

        // Macro
        Double usdInrChange,
        Double crudeReturn,
        Double goldReturn,
        Double us10yYieldChange
) {

    public GlobalMarketContext(
            Double giftNiftyReturn5m,
            Double giftNiftyReturn30m,
            Double giftNiftyOvernightReturn,
            Double sp500Return,
            Double nasdaqReturn,
            Double dowReturn,
            Double asianMarketReturn,
            Double indiaVix,
            Double indiaVixChange,
            Double usdInrChange,
            Double crudeReturn,
            Double goldReturn,
            Double us10yYieldChange
    ) {
        this(Instant.now(), giftNiftyReturn5m, giftNiftyReturn30m,
                giftNiftyOvernightReturn, sp500Return, nasdaqReturn,
                dowReturn, asianMarketReturn, indiaVix, indiaVixChange,
                usdInrChange, crudeReturn, goldReturn, us10yYieldChange);
    }

    public Map<String, Double> toFeatures() {

        Map<String, Double> features =
                new LinkedHashMap<>();

        put(features, "gift_nifty_return_5m", giftNiftyReturn5m);
        put(features, "gift_nifty_return_30m", giftNiftyReturn30m);
        put(features, "gift_nifty_overnight_return",
                giftNiftyOvernightReturn);

        put(features, "sp500_return", sp500Return);
        put(features, "nasdaq_return", nasdaqReturn);
        put(features, "dow_return", dowReturn);

        put(features, "asian_market_return",
                asianMarketReturn);

        put(features, "india_vix", indiaVix);
        put(features, "india_vix_change", indiaVixChange);

        put(features, "usd_inr_change", usdInrChange);
        put(features, "crude_return", crudeReturn);
        put(features, "gold_return", goldReturn);
        put(features, "us10y_yield_change",
                us10yYieldChange);

        return features;
    }

    private static void put(
            Map<String, Double> map,
            String key,
            Double value
    ) {
        if (value != null && Double.isFinite(value)) {
            map.put(key, value);
        }
    }
}