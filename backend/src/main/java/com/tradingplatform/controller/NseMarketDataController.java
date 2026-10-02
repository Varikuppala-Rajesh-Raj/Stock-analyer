package com.tradingplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.nse.NseFuturesContracts;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/nse")
public class NseMarketDataController {

    private static final Logger log = LoggerFactory.getLogger(NseMarketDataController.class);
    private static final DateTimeFormatter NSE_TIMESTAMP =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);

    private final RestClient nseClient;
    private volatile boolean sessionPrimed;
        private volatile JsonNode cachedFutures;
        private volatile Instant futuresCachedAt;

    public NseMarketDataController() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .cookieHandler(cookieManager)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.nseClient = RestClient.builder()
                .baseUrl("https://www.nseindia.com")
                .defaultHeader("Accept", "application/json, text/plain, */*")
            .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36")
            .defaultHeader("Referer", "https://www.nseindia.com/")
            .requestFactory(requestFactory)
                .build();
    }

    @GetMapping("/index-data")
    public JsonNode indexData(@RequestParam(defaultValue = "All") String type) {
        if (!type.equals("All") && !type.equals("INDIA VIX")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported NSE index type.");
        }

        return get("/api/NextApi/apiClient?functionName=getIndexData&type={type}", Map.of("type", type));
    }

    @GetMapping("/constituents")
    public JsonNode constituents() {
        return get(
                "/api/NextApi/apiClient/indexTrackerApi?functionName=getConstituents&index={index}&noofrecords=50",
                Map.of("index", "NIFTY 50")
        );
    }

    @GetMapping("/quote/{symbol}")
    public JsonNode equityQuote(@PathVariable String symbol) {
        if (!symbol.matches("[A-Za-z0-9&_-]{1,30}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid NSE equity symbol.");
        }
        return get(
                "/api/NextApi/apiClient/GetQuoteApi?functionName=getSymbolData&marketType=N&series=EQ&symbol={symbol}",
                Map.of("symbol", symbol.toUpperCase(Locale.ROOT))
        );
    }

    @GetMapping("/gift-nifty")
    public JsonNode giftNifty() {
        return get("/api/NextApi/apiClient?functionName=getGiftNifty", Map.of());
    }

    @GetMapping("/option-expiries")
    public JsonNode optionExpiries() {
        return get("/api/option-chain-contract-info?symbol=NIFTY", Map.of());
    }

    @GetMapping("/option-chain")
    public JsonNode optionChain(@RequestParam String expiry) {
        if (!expiry.matches("\\d{2}-[A-Za-z]{3}-\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry must use DD-MMM-YYYY format.");
        }

        return get(
                "/api/option-chain-v3?type=Indices&symbol=NIFTY&expiry={expiry}",
                Map.of("expiry", expiry)
        );
    }

    @GetMapping("/futures")
    public synchronized JsonNode futures() {
        Instant now = Instant.now();
        if (cachedFutures != null && futuresCachedAt != null
                && Duration.between(futuresCachedAt, now).compareTo(Duration.ofSeconds(20)) < 0) {
            return cachedFutures.deepCopy();
        }

        JsonNode contractInfo = getWithRetry("/api/option-chain-contract-info?symbol=NIFTY", Map.of());
        List<LocalDate> expiries = NseFuturesContracts.monthlyExpiries(
                contractInfo.path("expiryDates"), LocalDate.now(ZoneId.of("Asia/Kolkata")));
        log.info("[NSE-FUTURES] Discovered {} valid monthly NIFTY expiries", expiries.size());
        for (LocalDate expiry : expiries) {
            String identifier = NseFuturesContracts.identifier(expiry);
            String expiryText = expiry.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
            log.info("[NSE-FUTURES] Requesting contract {}", identifier);
            JsonNode quote = getWithRetry(
                    "/api/NextApi/apiClient/GetQuoteApi?functionName=getSymbolDerivativesData"
                            + "&symbol=NIFTY&identifier={identifier}&instrumentType=FUT&expiryDt={expiry}",
                    Map.of("identifier", identifier, "expiry", expiryText));
            if (NseFuturesContracts.isNiftyFutureQuote(quote, expiry)) {
                cachedFutures = quote.deepCopy();
                futuresCachedAt = now;
                JsonNode row = quote.path("data").get(0);
                log.info("[NSE-FUTURES] LTP={} OI={} ChangeOI={} Underlying={} Basis={} Timestamp={}",
                        row.path("lastPrice").asText(), row.path("openInterest").asText(),
                        row.path("changeinOpenInterest").asText(), row.path("underlyingValue").asText(),
                        row.path("lastPrice").asDouble() - row.path("underlyingValue").asDouble(),
                        quote.path("timestamp").asText());
                    Instant sourceTime = LocalDateTime.parse(quote.path("timestamp").asText(), NSE_TIMESTAMP)
                        .atZone(ZoneId.of("Asia/Kolkata")).toInstant();
                    Duration quoteAge = Duration.between(sourceTime, Instant.now());
                    boolean fresh = !quoteAge.isNegative() && quoteAge.compareTo(Duration.ofSeconds(60)) <= 0;
                    log.info("[NSE-FUTURES] Quote age={}s Status={}", quoteAge.toSeconds(), fresh ? "FRESH" : "STALE");
                return quote;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "NSE returned no valid, unexpired NIFTY index-futures contract.");
    }

    private JsonNode get(String path, Map<String, ?> variables) {
        return getWithRetry(path, variables);
    }

    private JsonNode getWithRetry(String path, Map<String, ?> variables) {
        primeSession();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return nseClient.get().uri(path, variables).retrieve().body(JsonNode.class);
            } catch (RestClientResponseException error) {
                int status = error.getStatusCode().value();
                if (status == 403 && attempt == 1) {
                    sessionPrimed = false;
                    primeSession();
                    continue;
                }
                if (attempt == 2 || (status != 429 && status < 500)) {
                    HttpStatus mapped = status == 403 || status == 429 || status >= 500
                            ? HttpStatus.BAD_GATEWAY : HttpStatus.valueOf(status);
                    throw new ResponseStatusException(mapped,
                            "NSE provider request failed with HTTP " + status + ".", error);
                }
            } catch (RestClientException error) {
                if (attempt == 2) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "NSE provider request timed out or was unavailable.", error);
                }
            }
            try {
                Thread.sleep(200L * attempt);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "NSE request retry was interrupted.", error);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NSE provider is unavailable.");
    }

    private synchronized void primeSession() {
        if (sessionPrimed) return;
        try {
            nseClient.get().uri("/").retrieve().toBodilessEntity();
            sessionPrimed = true;
        } catch (RestClientException error) {
            log.debug("[NSE] Homepage session bootstrap failed; attempting the API request directly.");
        }
    }
}