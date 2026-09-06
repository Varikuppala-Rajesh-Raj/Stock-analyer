package com.tradingplatform.signal;

import com.tradingplatform.market.Candle;
import com.tradingplatform.market.indicators.TechnicalIndicators;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Produces explainable market-state features from candles available at the analysis point.
 * The aggregate is a weighted market-state measure, deliberately not a strategy vote count.
 */
@Component
public class TechnicalStrategyEngine {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public TechnicalAnalysisResult analyze(MarketContext context) {
        List<StrategyResult> strategies = List.of(
                trend(context), momentum(context), volatility(context), breakout(context),
                meanReversion(context), volume(context), marketRegime(context), marketContext(context));
        Map<String, BigDecimal> features = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        for (StrategyResult strategy : strategies) {
            strategy.features().forEach(features::put);
            reasons.addAll(strategy.reasons());
        }
        int aggregate = clamp((int) Math.round(
                strategies.get(0).score() * .28 + strategies.get(1).score() * .22
                        + strategies.get(3).score() * .16 + strategies.get(5).score() * .10
                        + strategies.get(6).score() * .14 + strategies.get(7).score() * .10));
        Signal signal = aggregate >= 55 && strategies.get(0).score() > 0 && strategies.get(6).score() >= 0
                ? Signal.BUY : aggregate <= -55 && strategies.get(0).score() < 0 && strategies.get(6).score() <= 0
                ? Signal.SELL : Signal.HOLD;
        features.put("overall_technical_score", BigDecimal.valueOf(aggregate));
        return new TechnicalAnalysisResult(context.symbol(), context.timeframe(), aggregate, signal, strategies,
                regimeName(strategies.get(6)), Map.copyOf(features), List.copyOf(reasons));
    }

    private StrategyResult trend(MarketContext c) {
        var i = c.indicators(); BigDecimal price = c.price();
        BigDecimal slope = percent(i.ema20().subtract(TechnicalIndicators.ema(c.candles().subList(0, c.candles().size() - 5), 20)), i.ema20());
        boolean higherStructure = c.candles().get(c.candles().size() - 1).high().compareTo(c.candles().get(c.candles().size() - 6).high()) > 0
                && c.candles().get(c.candles().size() - 1).low().compareTo(c.candles().get(c.candles().size() - 6).low()) > 0;
        boolean lowerStructure = c.candles().get(c.candles().size() - 1).high().compareTo(c.candles().get(c.candles().size() - 6).high()) < 0
                && c.candles().get(c.candles().size() - 1).low().compareTo(c.candles().get(c.candles().size() - 6).low()) < 0;
        int score = (price.compareTo(i.ema9()) > 0 ? 18 : -18) + (i.ema9().compareTo(i.ema20()) > 0 ? 18 : -18)
                + (i.ema20().compareTo(i.ema50()) > 0 ? 24 : -24) + sign(slope) * 15
                + (higherStructure ? 15 : lowerStructure ? -15 : 0);
        if (i.ema200() != null) score += price.compareTo(i.ema200()) > 0 ? 10 : -10;
        String state = i.ema9().compareTo(i.ema20()) > 0 && i.ema20().compareTo(i.ema50()) > 0 ? "STRONG_BULLISH"
                : i.ema9().compareTo(i.ema20()) > 0 ? "SHORT_TERM_BULLISH_BROADER_BEARISH"
                : i.ema9().compareTo(i.ema20()) < 0 && i.ema20().compareTo(i.ema50()) < 0 ? "STRONG_BEARISH"
                : "SHORT_TERM_BEARISH_BROADER_BULLISH";
        return result("trend", score, Map.of("ema9", i.ema9(), "ema20", i.ema20(), "ema50", i.ema50(),
                "ema_slope_percent", slope, "price_to_ema20_percent", percent(price.subtract(i.ema20()), i.ema20()), "price_to_ema50_percent", percent(price.subtract(i.ema50()), i.ema50())),
                "Trend state: " + state + "; EMA alignment, slope and price structure evaluated");
    }

    private StrategyResult momentum(MarketContext c) {
        var i = c.indicators(); BigDecimal roc = TechnicalIndicators.momentum(c.candles(), 10);
        BigDecimal histogram = i.macd().subtract(i.macdSignal());
        int score = clamp((i.rsi14().subtract(BigDecimal.valueOf(50))).multiply(BigDecimal.valueOf(2)).intValue()
                + sign(histogram) * 25 + clamp(roc.multiply(BigDecimal.TEN).intValue()));
        if (i.rsi14().compareTo(BigDecimal.valueOf(70)) > 0 && c.price().compareTo(i.ema20()) > 0) score = Math.max(score, 15);
        String state = score > 30 && i.rsi14().compareTo(BigDecimal.valueOf(70)) > 0 ? "BULLISH_BUT_EXTENDED"
                : score < -30 && i.rsi14().compareTo(BigDecimal.valueOf(30)) < 0 ? "BEARISH_BUT_EXTENDED"
                : score > 0 ? "STRONG_BULLISH_MOMENTUM" : score < 0 ? "STRONG_BEARISH_MOMENTUM" : "NEUTRAL_MOMENTUM";
        return result("momentum", score, Map.of("rsi14", i.rsi14(), "macd", i.macd(), "macd_signal", i.macdSignal(),
                "macd_histogram", histogram, "roc10_percent", roc), "Momentum state: " + state);
    }

    private StrategyResult volatility(MarketContext c) {
        var i = c.indicators(); BigDecimal atrPercent = percent(i.atr14(), c.price());
        BigDecimal width = percent(i.upperBand().subtract(i.lowerBand()), c.price());
        String state = atrPercent.compareTo(BigDecimal.valueOf(3)) > 0 ? "HIGH_VOLATILITY" : atrPercent.compareTo(BigDecimal.valueOf(.35)) < 0 ? "LOW_VOLATILITY" : "NORMAL_VOLATILITY";
        return result("volatility", 0, Map.of("atr", i.atr14(), "atr_percent", atrPercent, "bollinger_width_percent", width), "Volatility state: " + state);
    }

    private StrategyResult breakout(MarketContext c) {
        var i = c.indicators(); Candle last = c.candles().get(c.candles().size() - 1);
        BigDecimal relativeVolume = ratio(last.volume(), i.volumeSma());
        boolean up = last.close().compareTo(i.recentHigh()) > 0, down = last.close().compareTo(i.recentLow()) < 0;
        boolean confirmed = relativeVolume.compareTo(BigDecimal.valueOf(1.2)) >= 0;
        int score = up ? (confirmed ? clamp(55 + relativeVolume.multiply(BigDecimal.valueOf(20)).intValue()) : 20) : down ? (confirmed ? clamp(-55 - relativeVolume.multiply(BigDecimal.valueOf(20)).intValue()) : -20) : 0;
        return result("breakout", score, Map.of("recent_high", i.recentHigh(), "recent_low", i.recentLow(), "relative_volume", relativeVolume,
                "breakout_strength", BigDecimal.valueOf(Math.abs(score))), up ? (confirmed ? "BULLISH_BREAKOUT confirmed by relative volume" : "FALSE_WEAK_BREAKOUT: resistance break lacks volume") : down ? (confirmed ? "BEARISH_BREAKOUT confirmed by relative volume" : "FALSE_WEAK_BREAKOUT: support break lacks volume") : "NO_BREAKOUT");
    }

    private StrategyResult meanReversion(MarketContext c) {
        var i = c.indicators(); BigDecimal vwapDistance = percent(c.price().subtract(i.vwap()), i.vwap());
        BigDecimal emaDistance = percent(c.price().subtract(i.ema20()), i.ema20());
        int score = clamp(emaDistance.multiply(BigDecimal.TEN).intValue() + vwapDistance.multiply(BigDecimal.valueOf(8)).intValue());
        return result("mean_reversion", score, Map.of("vwap_distance_percent", vwapDistance, "ema20_distance_percent", emaDistance,
                "upper_band_distance_percent", percent(c.price().subtract(i.upperBand()), i.upperBand())),
                score > 25 ? "BULLISH_EXTENSION: possible mean reversion, not a trade signal" : score < -25 ? "BEARISH_EXTENSION: possible mean reversion, not a trade signal" : "NORMAL mean-reversion state");
    }

    private StrategyResult volume(MarketContext c) {
        Candle last = c.candles().get(c.candles().size() - 1); BigDecimal relative = ratio(last.volume(), c.indicators().volumeSma());
        int direction = c.price().compareTo(c.indicators().ema20()); int score = clamp(relative.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(45)).intValue() * direction);
        return result("volume", score, Map.of("current_volume", last.volume(), "average_volume20", c.indicators().volumeSma(), "relative_volume", relative),
                relative.compareTo(BigDecimal.ONE) > 0 ? "Volume is above its 20-candle average" : "Volume confirmation is limited");
    }

    private StrategyResult marketRegime(MarketContext c) {
        var i = c.indicators(); BigDecimal atrPercent = percent(i.atr14(), c.price());
        int score = c.price().compareTo(i.ema50()) > 0 && i.ema20().compareTo(i.ema50()) > 0 ? 65
                : c.price().compareTo(i.ema50()) < 0 && i.ema20().compareTo(i.ema50()) < 0 ? -65 : 0;
        if (atrPercent.compareTo(BigDecimal.valueOf(3)) > 0) score = 0;
        String state = score > 0 ? "TRENDING_UP" : score < 0 ? "TRENDING_DOWN" : atrPercent.compareTo(BigDecimal.valueOf(3)) > 0 ? "HIGH_VOLATILITY" : atrPercent.compareTo(BigDecimal.valueOf(.35)) < 0 ? "LOW_VOLATILITY" : "RANGING";
        return result("market_regime", score, Map.of("atr_percent", atrPercent, "regime_score", BigDecimal.valueOf(score)), "Market regime: " + state);
    }

    private StrategyResult marketContext(MarketContext c) {
        if (c.indexContext() == null) return result("market_context", 0, Map.of("context_available", BigDecimal.ZERO, "market_context_score", BigDecimal.ZERO), "Index context unavailable; local analysis continues");
        return result("market_context", c.indexContext().score(), Map.of("context_available", BigDecimal.ONE, "market_context_score", BigDecimal.valueOf(c.indexContext().score())), c.indexContext().reason());
    }

    private static StrategyResult result(String name, int score, Map<String, BigDecimal> features, String reason) { int bounded = clamp(score); return new StrategyResult(name, bounded > 0 ? Direction.BULLISH : bounded < 0 ? Direction.BEARISH : Direction.NEUTRAL, bounded, List.of(reason), features); }
    private static int clamp(int score) { return Math.max(-100, Math.min(100, score)); }
    private static int sign(BigDecimal value) { return Integer.compare(value.signum(), 0); }
    private static BigDecimal ratio(BigDecimal value, BigDecimal base) { return base.signum() == 0 ? BigDecimal.ZERO : value.divide(base, 6, RoundingMode.HALF_UP); }
    private static BigDecimal percent(BigDecimal value, BigDecimal base) { return base.signum() == 0 ? BigDecimal.ZERO : value.multiply(HUNDRED).divide(base, 6, RoundingMode.HALF_UP); }
    private static String regimeName(StrategyResult result) { return result.reasons().get(0).replace("Market regime: ", ""); }
}
