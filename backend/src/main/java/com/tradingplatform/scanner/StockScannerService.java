package com.tradingplatform.scanner;

import com.tradingplatform.market.*; import com.tradingplatform.signal.*; import java.util.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
@Service public class StockScannerService {
 private final InstrumentCatalogService catalog; private final AnalysisService analysis; private final int maxCandidates;
 public StockScannerService(InstrumentCatalogService catalog, AnalysisService analysis,@Value("${trading.scanner.max-candidates:100}") int maxCandidates) { this.catalog=catalog; this.analysis=analysis; this.maxCandidates=maxCandidates; }
 public ScannerResponse scan(Timeframe timeframe, Signal filter, Integer minimumScore, int limit) {
   if(limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
   List<ScannerResult> results=new ArrayList<>();
   // The scanner is intentionally bounded: MarketDataService still serves Redis hits first,
   // but a cache miss must never fan out across the complete instrument catalogue.
   for(Instrument instrument:catalog.active().stream().limit(maxCandidates).toList()) try { SignalResult signal=analysis.analyze(instrument.symbol(),timeframe); if((filter==null||signal.signal()==filter) && (minimumScore==null||signal.signalStrength()>=minimumScore)) results.add(new ScannerResult(instrument.symbol(),signal.signal(),signal.bullishScore(),signal.bearishScore(),signal.signalStrength(),signal.entry(),signal.entry(),signal.stopLoss(),signal.target(),signal.riskReward(),signal.reasons(),signal.strategies())); } catch(MarketDataUnavailableException | IllegalArgumentException ignored) { /* one unavailable instrument does not fail the universe */ }
   results.sort(Comparator.comparingInt(ScannerResult::signalStrength).reversed().thenComparing(r -> signalPriority(r.signal())).thenComparing(ScannerResult::symbol));
   return new ScannerResponse(java.time.Instant.now(),timeframe,results.stream().limit(limit).toList());
 }
 private static int signalPriority(Signal signal) { return switch(signal) { case BUY -> 0; case SELL -> 1; case HOLD -> 2; }; }
}
