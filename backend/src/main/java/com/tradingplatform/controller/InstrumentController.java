package com.tradingplatform.controller;

import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.InstrumentCatalogService;
import com.tradingplatform.market.InstrumentImportReport;
import com.tradingplatform.market.InstrumentImportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentCatalogService catalog;
    private final InstrumentImportService importer;

    public InstrumentController(
            InstrumentCatalogService catalog,
            InstrumentImportService importer
    ) {
        this.catalog = catalog;
        this.importer = importer;
    }

    /**
     * Get all active instruments.
     *
     * Example:
     *
     * GET /api/instruments
     */
    @GetMapping
    public List<Instrument> activeInstruments() {
        return catalog.active();
    }

    /**
     * Search/resolve a specific symbol.
     *
     * Example:
     *
     * GET /api/instruments/TCS
     */
    @GetMapping("/{symbol}")
    public Instrument getInstrument(
            @PathVariable String symbol
    ) {
        return catalog.resolveSymbol(symbol);
    }

    /**
     * Refresh instrument catalog from Upstox.
     *
     * Example:
     *
     * POST /api/instruments/import
     */
    @PostMapping("/import")
    public InstrumentImportReport importInstruments() {
        return importer.importConfiguredSource();
    }
}