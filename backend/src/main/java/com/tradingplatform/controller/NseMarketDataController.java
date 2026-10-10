package com.tradingplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradingplatform.market.nse.NseMarketDataService;
import com.tradingplatform.market.nse.NseReferenceSnapshotService;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/nse")
public class NseMarketDataController {
    private final NseMarketDataService marketData;
    private final NseReferenceSnapshotService referenceSnapshots;

    public NseMarketDataController(
            NseMarketDataService marketData,
            NseReferenceSnapshotService referenceSnapshots) {
        this.marketData = marketData;
        this.referenceSnapshots = referenceSnapshots;
    }

    @GetMapping("/index-data")
    public JsonNode indexData(@RequestParam(defaultValue = "All") String type) {
        if (!type.equals("All") && !type.equals("INDIA VIX")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported NSE index type.");
        }

        if (type.equals("INDIA VIX")) {
            return indiaVixResponse();
        }

        return marketData.indexData(type);
    }

    @GetMapping("/premarket")
    public JsonNode premarket() {
        ObjectNode response = JsonNodeFactory.instance.objectNode();
        try {
            response.set("indiaVix", indiaVixData());
        } catch (ResponseStatusException error) {
            response.put("indiaVixError", error.getReason() == null
                    ? "NSE India VIX request failed." : error.getReason());
        }
        try {
            response.set("fiiDii", fiiDiiData());
        } catch (ResponseStatusException error) {
            response.put("fiiDiiError", error.getReason() == null
                    ? "NSE FII/DII request failed." : error.getReason());
        }
        return response;
    }

    @GetMapping("/fii-dii")
    public JsonNode fiiDii() {
        return fiiDiiData();
    }

    @GetMapping("/index-candles")
    public JsonNode indexCandles() {
        return marketData.indexCandles();
    }

    @GetMapping("/constituents")
    public JsonNode constituents() {
        return marketData.constituents();
    }

    @GetMapping("/quote/{symbol}")
    public JsonNode equityQuote(@PathVariable String symbol) {
        if (!symbol.matches("[A-Za-z0-9&_-]{1,30}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid NSE equity symbol.");
        }
        return marketData.equityQuote(symbol.toUpperCase(Locale.ROOT));
    }

    @GetMapping("/gift-nifty")
    public JsonNode giftNifty() {
        return marketData.giftNifty();
    }

    @GetMapping("/option-expiries")
    public JsonNode optionExpiries() {
        return marketData.optionExpiries();
    }

    @GetMapping("/option-chain")
    public JsonNode optionChain(@RequestParam String expiry) {
        if (!expiry.matches("\\d{2}-[A-Za-z]{3}-\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry must use DD-MMM-YYYY format.");
        }

        return marketData.optionChain(expiry);
    }

    @GetMapping("/futures")
    public JsonNode futures() {
        return marketData.futures();
    }

    private JsonNode indiaVixResponse() {
        ObjectNode response = JsonNodeFactory.instance.objectNode();
        response.putArray("data").add(indiaVixData());
        return response;
    }

    private JsonNode indiaVixData() {
        JsonNode payload = marketData.allIndices();
        return referenceSnapshots.persistIndiaVix(findIndiaVix(payload));
    }

    private JsonNode fiiDiiData() {
        JsonNode payload = marketData.fiiDii();
        referenceSnapshots.persistFiiDii(payload);
        return payload;
    }

    private static JsonNode findIndiaVix(JsonNode payload) {
        JsonNode indices = payload.path("data");
        if (indices.isArray()) {
            for (JsonNode row : indices) {
                if ("INDIA VIX".equalsIgnoreCase(row.path("index").asText())
                        || "INDIA VIX".equalsIgnoreCase(row.path("indexSymbol").asText())) {
                    return row;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "NSE all-indices response did not contain India VIX.");
    }
}