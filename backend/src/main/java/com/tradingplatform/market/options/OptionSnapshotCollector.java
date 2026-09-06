package com.tradingplatform.market.options;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.indicators.CanonicalTechnicalFeatures;
import com.tradingplatform.signal.AnalysisService;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OptionSnapshotCollector {
    private final NiftyOptionChainService chains; private final OptionSnapshotService snapshots; private final MarketDataService market; private final AnalysisService analysis; private final boolean enabled;
    public OptionSnapshotCollector(NiftyOptionChainService chains, OptionSnapshotService snapshots, MarketDataService market, AnalysisService analysis, @Value("${trading.options.collection-enabled:false}") boolean enabled) { this.chains=chains; this.snapshots=snapshots; this.market=market; this.analysis=analysis; this.enabled=enabled; }
    @Scheduled(cron = "${trading.options.collection-cron:0 */5 9-15 * * MON-FRI}", zone = "Asia/Kolkata")
    public void collectDuringMarketHours() { if (enabled && marketHours()) chains.nearestNiftyExpiry().ifPresent(this::collect); }
    public int collect(String expiry) {
        List<Candle> candles=market.intraday("NIFTY", Timeframe.M5);
        Map<String,Double> features=CanonicalTechnicalFeatures.from(analysis.analyze("NIFTY", Timeframe.M5, candles).technicalAnalysis()).entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().doubleValue()));
        JsonNode chain=chains.getNiftyOptionChain(expiry).path("data"); if (!chain.isArray() || chain.isEmpty()) throw new IllegalArgumentException("No NIFTY option chain available for expiry: " + expiry);
        return snapshots.persist(expiry, chain, features);
    }
    private boolean marketHours() { LocalTime now=ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalTime(); return !now.isBefore(LocalTime.of(9,15)) && !now.isAfter(LocalTime.of(15,30)); }
}
