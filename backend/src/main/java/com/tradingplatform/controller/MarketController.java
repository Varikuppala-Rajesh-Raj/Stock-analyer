package com.tradingplatform.controller;
import com.tradingplatform.market.*; import java.time.LocalDate; import java.util.List; import org.springframework.format.annotation.DateTimeFormat; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/market") public class MarketController {
 private final MarketDataService market;
 public MarketController(MarketDataService market) { this.market=market; }
 @GetMapping("/quote/{symbol}") public Quote quote(@PathVariable String symbol) { return market.quote(symbol); }
 @GetMapping("/history/{symbol}") public List<Candle> history(@PathVariable String symbol, @RequestParam String timeframe, @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) { return market.history(symbol, Timeframe.parse(timeframe), from, to); }
 @GetMapping("/intraday/{symbol}") public List<Candle> intraday(@PathVariable String symbol, @RequestParam String timeframe) { return market.intraday(symbol, Timeframe.parse(timeframe)); }
}
