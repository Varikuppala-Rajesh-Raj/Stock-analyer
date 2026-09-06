package com.tradingplatform.market.context;

import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataNormalizer;
import com.tradingplatform.market.indicators.FeatureVector;
import com.tradingplatform.market.indicators.TechnicalFeatureService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Composes point-in-time technical, option, global, and news context. */
@Service
public class MarketContextService {

    private final TechnicalFeatureService technicalFeatures;
    private final GlobalMarketContextService globalContext;
    private final NewsContextService newsContext;

    public MarketContextService(TechnicalFeatureService technicalFeatures,
                                GlobalMarketContextService globalContext,
                                NewsContextService newsContext) {
        this.technicalFeatures = technicalFeatures;
        this.globalContext = globalContext;
        this.newsContext = newsContext;
    }

    public MarketContext snapshot(Instant asOf, List<Candle> candles,
                                  Map<String, Double> optionFeatures) {
        if (asOf == null) {
            throw new IllegalArgumentException("Context timestamp cannot be null.");
        }

        List<Candle> available = MarketDataNormalizer.normalizeCandles(candles).stream()
                .filter(candle -> !candle.timestamp().isAfter(asOf))
                .toList();
        FeatureVector technical = technicalFeatures.calculate(available);
        GlobalMarketContext global = globalContext.snapshot(asOf);
        NewsContext news = newsContext.snapshot(asOf);

        return new MarketContext(asOf, toFeatures(technical), optionFeatures,
                global.toFeatures(), news.toFeatures());
    }

    public MarketContext snapshot(Instant asOf, List<Candle> candles) {
        return snapshot(asOf, candles, Map.of());
    }

    private static Map<String, Double> toFeatures(FeatureVector vector) {
        Map<String, Double> features = new LinkedHashMap<>();
        put(features, "close", vector.close());
        put(features, "sma20", vector.sma20());
        put(features, "sma50", vector.sma50());
        put(features, "ema9", vector.ema9());
        put(features, "ema20", vector.ema20());
        put(features, "ema50", vector.ema50());
        put(features, "rsi14", vector.rsi14());
        put(features, "macd", vector.macd());
        put(features, "macd_signal", vector.macdSignal());
        put(features, "macd_histogram", vector.macdHistogram());
        put(features, "atr14", vector.atr14());
        put(features, "momentum", vector.momentum());
        put(features, "volume", vector.volume());
        put(features, "average_volume20", vector.averageVolume20());
        put(features, "volume_ratio", vector.volumeRatio());
        put(features, "volatility", vector.volatility());
        if (vector.technicalStrategyFeatures() != null) {
            vector.technicalStrategyFeatures().forEach((key, value) -> put(features, key, value));
        }
        return features;
    }

    private static void put(Map<String, Double> features, String key, BigDecimal value) {
        if (value != null && value.doubleValue() >= -Double.MAX_VALUE
                && value.doubleValue() <= Double.MAX_VALUE) {
            features.put(key, value.doubleValue());
        }
    }
}