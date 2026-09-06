package com.tradingplatform.controller;
import com.tradingplatform.market.Timeframe; import com.tradingplatform.scanner.*; import com.tradingplatform.signal.Signal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/scanner") public class ScannerController {
 private final StockScannerService scanner; public ScannerController(StockScannerService scanner) {this.scanner=scanner;}
 @GetMapping public ScannerResponse scan(@RequestParam(defaultValue="1D") String timeframe,@RequestParam(required=false) Signal signal,@RequestParam(required=false) Integer minimumScore,@RequestParam(defaultValue="20") int limit){return scanner.scan(Timeframe.parse(timeframe),signal,minimumScore,limit);}
}
