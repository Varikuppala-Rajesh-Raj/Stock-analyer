package com.tradingplatform.controller;

import com.tradingplatform.market.context.OvernightGlobalMarketService;
import com.tradingplatform.market.context.OvernightGlobalSnapshot;
import com.tradingplatform.market.context.OvernightRetrieveRequest;
import com.tradingplatform.market.context.OvernightRetrieveResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overnight")
public class OvernightMarketController {

    private final OvernightGlobalMarketService overnightMarket;

    public OvernightMarketController(OvernightGlobalMarketService overnightMarket) {
        this.overnightMarket = overnightMarket;
    }

    @GetMapping("/global")
    public OvernightGlobalSnapshot global() {
        return overnightMarket.snapshot();
    }

    @GetMapping("/quotes")
    public OvernightRetrieveResponse savedQuotes() {
        return overnightMarket.savedQuotes();
    }

    @PostMapping("/retrieve")
    public OvernightRetrieveResponse retrieve(@RequestBody(required = false) OvernightRetrieveRequest request) {
        return overnightMarket.retrieve(request == null ? null : request.instrument());
    }

    @PostMapping("/refresh")
    public OvernightRetrieveResponse refresh() {
        return overnightMarket.refreshAllFromProvider();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidInstrument(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(error.getMessage()));
    }

    public record ErrorResponse(String message) {
    }
}
