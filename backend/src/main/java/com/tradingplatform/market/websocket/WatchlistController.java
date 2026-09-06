package com.tradingplatform.market.websocket;

import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.InstrumentCatalogService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final InstrumentCatalogService catalog;
    private final MarketStreamService marketStream;
public WatchlistController(
        InstrumentCatalogService catalog,
        @Qualifier("upstoxMarketStreamService")
        MarketStreamService marketStream
) {
    this.catalog = catalog;
    this.marketStream = marketStream;
}

    @PostMapping("/{symbol}/subscribe")
    public ResponseEntity<?> subscribe(
            @PathVariable String symbol
    ) {

        Instrument instrument =
                catalog.resolveSymbol(symbol);

        marketStream.subscribe(
                Set.of(instrument.instrumentKey())
        );

        return ResponseEntity.ok(
                Map.of(
                        "symbol", instrument.symbol(),
                        "instrumentKey", instrument.instrumentKey(),
                        "status", "SUBSCRIBED"
                )
        );
    }

    @DeleteMapping("/{symbol}/subscribe")
    public ResponseEntity<?> unsubscribe(
            @PathVariable String symbol
    ) {

        Instrument instrument =
                catalog.resolveSymbol(symbol);

        marketStream.unsubscribe(
                Set.of(instrument.instrumentKey())
        );

        return ResponseEntity.ok(
                Map.of(
                        "symbol", instrument.symbol(),
                        "instrumentKey", instrument.instrumentKey(),
                        "status", "UNSUBSCRIBED"
                )
        );
    }

    @GetMapping("/stream")
    public ResponseEntity<?> streamStatus() {

        if (marketStream instanceof UpstoxMarketStreamService upstox) {

            return ResponseEntity.ok(
                    Map.of(
                            "status", upstox.status().name(),
                            "instruments",
                            upstox.subscribedInstruments()
                    )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        marketStream.status().name()
                )
        );
    }
}