package com.tradingplatform.controller;
import com.tradingplatform.market.Timeframe; import com.tradingplatform.signal.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") public class AnalysisController {
 private final AnalysisService analysis; public AnalysisController(AnalysisService analysis){this.analysis=analysis;}
 @GetMapping("/analysis/{symbol}") public SignalResult analyze(@PathVariable String symbol,@RequestParam(defaultValue="1D") String timeframe){return analysis.analyze(symbol,Timeframe.parse(timeframe));}
}
