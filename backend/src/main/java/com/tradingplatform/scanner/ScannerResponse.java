package com.tradingplatform.scanner;
import com.tradingplatform.market.Timeframe; import java.time.Instant; import java.util.List;
public record ScannerResponse(Instant timestamp, Timeframe timeframe, List<ScannerResult> results) {}
