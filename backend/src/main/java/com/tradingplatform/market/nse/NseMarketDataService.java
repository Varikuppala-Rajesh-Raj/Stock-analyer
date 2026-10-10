package com.tradingplatform.market.nse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradingplatform.market.Quote;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NseMarketDataService {
    private static final Logger log = LoggerFactory.getLogger(NseMarketDataService.class);
    private static final DateTimeFormatter NSE_TIMESTAMP =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter NSE_TIMESTAMP_MINUTES =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);

    private final RestClient nseClient;
    private volatile boolean sessionPrimed;
    private volatile JsonNode cachedFutures;
    private volatile Instant futuresCachedAt;

    @Autowired
    public NseMarketDataService() {
        this(createNseClient());
    }

    public NseMarketDataService(RestClient nseClient) {
        this.nseClient = nseClient;
    }

    private static RestClient createNseClient() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(cookieManager)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder()
                .baseUrl("https://www.nseindia.com")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36")
                .defaultHeader("Referer", "https://www.nseindia.com/")
                .requestFactory(requestFactory)
                .build();
    }

    public JsonNode indexData(String type) {
        return get("/api/NextApi/apiClient?functionName=getIndexData&type={type}", Map.of("type", type));
    }

    public JsonNode allIndices() {
        return get("/api/allIndices", Map.of());
    }

    public JsonNode fiiDii() {
        JsonNode payload = get("/api/fiidiiTradeReact", Map.of());
        if (payload.isArray()) {
            ObjectNode normalized = JsonNodeFactory.instance.objectNode();
            normalized.set("value", payload);
            normalized.put("Count", payload.size());
            return normalized;
        }
        if (!payload.path("value").isArray()) {
            List<String> fields = new java.util.ArrayList<>();
            if (payload.isObject()) payload.fieldNames().forEachRemaining(fields::add);
            log.warn("[NSE-FII-DII] Provider response has no value array (type={}, fields={}).",
                    payload.getNodeType(), fields);
        }
        return payload;
    }

    public JsonNode indexCandles() {
        return get("/api/chart-databyindex?index={index}&indices=true", Map.of("index", "NIFTY 50"));
    }

    public JsonNode constituents() {
        return get(
                "/api/NextApi/apiClient/indexTrackerApi?functionName=getConstituents&index={index}&noofrecords=50",
                Map.of("index", "NIFTY 50"));
    }

    public JsonNode equityQuote(String symbol) {
        return get(
                "/api/NextApi/apiClient/GetQuoteApi?functionName=getSymbolData&marketType=N&series=EQ&symbol={symbol}",
                Map.of("symbol", symbol));
    }

    public JsonNode giftNifty() {
        return get("/api/NextApi/apiClient?functionName=getGiftNifty", Map.of());
    }

    public Quote overnightNseQuote(String instrument) {
        String code = instrument.toUpperCase(Locale.ROOT);
        String symbol;
        List<String> failures = new java.util.ArrayList<>();
        Quote quote;
        switch (code) {
            case "USD_INR" -> {
                symbol = "USDINR";
                quote = fetchOvernightQuote("/api/quote-currency?key=INR", Map.of(), symbol, false, failures);
                if (quote != null) return quote;
                quote = fetchOvernightQuote("/api/historicalOR/cd/derivatives?symbol={symbol}",
                        Map.of("symbol", symbol), symbol, false, failures);
            }
            case "BRENT" -> {
                symbol = "BRCRUDEOIL";
                quote = fetchOvernightQuote("/api/quotes-commodity-derivatives-main"
                        + "?category=Energy&symbol={symbol}", Map.of("symbol", symbol), symbol, false, failures);
                if (quote != null) return quote;
                quote = fetchOvernightQuote("/json/quotes/commodity-all-contracts.json", Map.of(),
                        symbol, true, failures);
            }
            case "CRUDE_OIL" -> {
                symbol = "CRUDEOIL";
                quote = fetchOvernightQuote("/api/quotes-commodity-derivatives-main"
                        + "?category=Energy&symbol={symbol}", Map.of("symbol", symbol), symbol, false, failures);
                if (quote != null) return quote;
                quote = fetchOvernightQuote("/json/quotes/commodity-all-contracts.json", Map.of(),
                        symbol, true, failures);
            }
            case "GOLD" -> {
                symbol = "GOLD";
                CommodityFutureContract contract;
                try {
                    contract = activeGoldFuture(get("/api/quotes-commodity-master-filter?symbol=GOLD", Map.of()));
                } catch (ResponseStatusException error) {
                    failures.add("/api/quotes-commodity-master-filter failed: " + error.getMessage());
                    contract = null;
                }
                if (contract == null) {
                    failures.add("Gold master returned no active futures contract");
                    quote = null;
                } else {
                    quote = fetchGoldFutureQuote(contract, failures);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported NSE overnight instrument: " + instrument);
        }
        if (quote != null) return quote;
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "NSE returned no usable " + symbol + " quote. Attempts: " + String.join("; ", failures));
    }

    private Quote fetchGoldFutureQuote(CommodityFutureContract contract, List<String> failures) {
        String path = "/api/quotes-commodity-derivatives-main?category=Bullion&symbol=GOLD"
                + "&instrumentType={instrumentType}&expiryDate={expiryDate}";
        Map<String, ?> parameters = Map.of(
                "instrumentType", contract.instrumentType(),
                "expiryDate", contract.expiryText());
        try {
            JsonNode payload = get(path, parameters);
            JsonNode selected = matchingGoldFutureQuote(payload, contract);
            if (selected == null) {
                failures.add(path + " returned no quote for the selected active futures contract");
                return null;
            }
            return quoteFromCandidate(selected, payload, "GOLD",
                    "NSE_COM|GOLD|" + contract.expiryText());
        } catch (ResponseStatusException error) {
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            failures.add(path + " failed: " + reason);
            log.warn("[NSE-OVERNIGHT] symbol=GOLD expiry={} endpoint={} failed: {}",
                    contract.expiryText(), path, reason);
            return null;
        }
    }

    private static CommodityFutureContract activeGoldFuture(JsonNode payload) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<CommodityFutureContract> contracts = new java.util.ArrayList<>();
        collectGoldFutureContracts(payload, today, contracts);
        return contracts.stream()
                .min(java.util.Comparator.comparing(CommodityFutureContract::expiry))
                .orElse(null);
    }

    private static void collectGoldFutureContracts(
            JsonNode node,
            LocalDate today,
            List<CommodityFutureContract> contracts
    ) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            String symbol = firstText(node, "symbol", "underlying", "underlyingSymbol", "product");
            String instrumentType = firstText(node, "instrumentType", "instrumenttype", "contractType");
            String optionType = firstText(node, "optionType", "optiontype");
            String expiryText = firstText(node, "expiryDate", "expiry", "expiryDt", "maturityDate");
            LocalDate expiry = parseExpiry(expiryText);
            if (symbol != null && symbol.equalsIgnoreCase("GOLD")
                    && isGoldFuturesType(instrumentType) && !isOptionType(optionType)
                    && expiry != null && !expiry.isBefore(today)) {
                contracts.add(new CommodityFutureContract(instrumentType, expiryText, expiry));
            }
            node.elements().forEachRemaining(child -> collectGoldFutureContracts(child, today, contracts));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectGoldFutureContracts(child, today, contracts));
        }
    }

    private static boolean isGoldFuturesType(String instrumentType) {
        if (instrumentType == null) return false;
        String normalized = instrumentType.toUpperCase(Locale.ROOT);
        return normalized.startsWith("FUT") && !normalized.startsWith("OPTFUT");
    }

    private static boolean isOptionType(String optionType) {
        if (optionType == null || optionType.isBlank()) return false;
        String normalized = optionType.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("CE") || normalized.equals("PE")
                || normalized.contains("CALL") || normalized.contains("PUT");
    }

    private static LocalDate parseExpiry(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeException ignored) {
            try {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
            } catch (DateTimeException ignoredNse) {
                return null;
            }
        }
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static JsonNode matchingGoldFutureQuote(JsonNode payload, CommodityFutureContract contract) {
        List<JsonNode> candidates = new java.util.ArrayList<>();
        collectQuoteCandidates(payload, candidates);
        return candidates.stream()
                .filter(node -> hasMatchingSymbol(node, "GOLD"))
                .filter(node -> {
                    String type = firstText(node, "instrumentType", "instrumenttype", "contractType");
                    return type == null || isGoldFuturesType(type);
                })
                .filter(node -> !isOptionType(firstText(node, "optionType", "optiontype")))
                .filter(node -> {
                    String expiryText = firstText(node, "expiryDate", "expiry", "expiryDt", "maturityDate");
                    LocalDate expiry = parseExpiry(expiryText);
                    return expiry == null || expiry.equals(contract.expiry());
                })
                .max(java.util.Comparator.comparingInt(node -> quoteCandidateScore(node, "GOLD")))
                .orElse(null);
    }

    private Quote fetchOvernightQuote(
            String path,
            Map<String, ?> variables,
            String symbol,
            boolean requireSymbolMatch,
            List<String> failures
    ) {
        try {
            JsonNode payload = get(path, variables);
            Quote quote = quoteFromPayload(payload, symbol, "NSE_API|" + symbol, requireSymbolMatch);
            if (quote == null) {
                failures.add(path + " returned no valid matching quote");
                return null;
            }
            return quote;
        } catch (ResponseStatusException error) {
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            failures.add(path + " failed: " + reason);
            log.warn("[NSE-OVERNIGHT] symbol={} endpoint={} failed: {}",
                    symbol, path, reason);
            return null;
        }
    }

    public JsonNode optionExpiries() {
        return get("/api/option-chain-contract-info?symbol=NIFTY", Map.of());
    }

    public JsonNode optionChain(String expiry) {
        return get(
                "/api/option-chain-v3?type=Indices&symbol=NIFTY&expiry={expiry}",
                Map.of("expiry", expiry));
    }

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

    private static Quote quoteFromPayload(
            JsonNode payload,
            String symbol,
            String instrumentKey,
            boolean requireSymbolMatch
    ) {
        if (payload == null || payload.isNull()) return null;
        List<JsonNode> candidates = new java.util.ArrayList<>();
        collectQuoteCandidates(payload, candidates);
        JsonNode selected = candidates.stream()
                .filter(node -> !requireSymbolMatch || hasMatchingSymbol(node, symbol))
                .max(java.util.Comparator.comparingInt(node -> quoteCandidateScore(node, symbol)))
                .orElse(null);
        if (selected == null) return null;

        return quoteFromCandidate(selected, payload, symbol, instrumentKey);
    }

    private static Quote quoteFromCandidate(
            JsonNode selected,
            JsonNode payload,
            String symbol,
            String instrumentKey
    ) {
        BigDecimal price = firstDecimal(selected, "lastPrice", "lastprice", "ltp", "last", "price",
                "closePrice", "close", "value");
        if (price == null || price.signum() <= 0) return null;
        BigDecimal change = firstDecimal(selected, "netChange", "change", "changeInPrice", "variation");
        BigDecimal previousClose = firstDecimal(selected, "previousClose", "prevClose", "prevDayClose",
                "previousClosingPrice", "closePrice");
        if (previousClose == null && change != null) {
            previousClose = price.subtract(change);
        }
        if (change == null && previousClose != null) {
            change = price.subtract(previousClose);
        }
        BigDecimal changePercent = firstDecimal(selected, "pChange", "perChange", "percentChange",
                "changePercent", "percChange");
        if (changePercent == null && change != null && previousClose != null && previousClose.signum() != 0) {
            changePercent = change.multiply(BigDecimal.valueOf(100))
                    .divide(previousClose, 6, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal open = firstDecimal(selected, "open", "openPrice");
        BigDecimal high = firstDecimal(selected, "high", "highPrice");
        BigDecimal low = firstDecimal(selected, "low", "lowPrice");
        BigDecimal volume = firstDecimal(selected, "volume", "totalTradedVolume", "totalTradedQty");
        Instant timestamp = firstInstant(selected, payload, "timestamp", "lastUpdateTime", "timeStamp",
                "lastUpdated", "updatedAt");
        return new Quote(symbol, instrumentKey, price, open, high, low, previousClose, change,
                changePercent, volume, timestamp);
    }

    private static void collectQuoteCandidates(JsonNode node, List<JsonNode> candidates) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            if (firstDecimal(node, "lastPrice", "lastprice", "ltp", "last", "price",
                    "closePrice", "close", "value") != null) {
                candidates.add(node);
            }
            node.elements().forEachRemaining(child -> collectQuoteCandidates(child, candidates));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectQuoteCandidates(child, candidates));
        }
    }

    private static int quoteCandidateScore(JsonNode node, String symbol) {
        int score = 0;
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String name = field.getKey().toLowerCase(Locale.ROOT);
            if (name.contains("symbol") || name.contains("identifier") || name.contains("instrument")
                    || name.contains("contract") || name.equals("name")) {
                String value = field.getValue().asText("").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
                String target = symbol.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
                if (value.equals(target)) score += 100;
                else if (value.contains(target)) score += 50;
            }
        }
        if (firstInstant(node, node, "timestamp", "lastUpdateTime", "timeStamp", "lastUpdated") != null) {
            score += 2;
        }
        if (firstDecimal(node, "netChange", "change", "variation") != null) score++;
        return score;
    }

    private static boolean hasMatchingSymbol(JsonNode node, String symbol) {
        String target = symbol.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String name = field.getKey().toLowerCase(Locale.ROOT);
            if (name.contains("symbol") || name.contains("identifier") || name.contains("instrument")
                    || name.contains("contract") || name.equals("name")) {
                String value = field.getValue().asText("").toUpperCase(Locale.ROOT)
                        .replaceAll("[^A-Z0-9]", "");
                if (value.contains(target)) return true;
            }
        }
        return false;
    }

    private record CommodityFutureContract(String instrumentType, String expiryText, LocalDate expiry) {
    }

    private static BigDecimal firstDecimal(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) continue;
            try {
                String text = value.asText().replace(",", "").trim();
                BigDecimal parsed = new BigDecimal(text);
                if (parsed.abs().compareTo(BigDecimal.valueOf(1_000_000_000_000L)) < 0) return parsed;
            } catch (NumberFormatException ignored) {
                // Try the next provider field alias.
            }
        }
        return null;
    }

    private static Instant firstInstant(JsonNode candidate, JsonNode payload, String... names) {
        Instant value = parseInstant(candidate, names);
        return value != null ? value : parseInstant(payload, names);
    }

    private static Instant parseInstant(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull() || !value.isValueNode()) continue;
            String text = value.asText().trim();
            if (text.isEmpty()) continue;
            try {
                return Instant.parse(text);
            } catch (DateTimeException ignored) {
                try {
                    return OffsetDateTime.parse(text).toInstant();
                } catch (DateTimeException ignoredOffset) {
                    try {
                        return LocalDateTime.parse(text, NSE_TIMESTAMP).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
                    } catch (DateTimeException ignoredSeconds) {
                        try {
                            return LocalDateTime.parse(text, NSE_TIMESTAMP_MINUTES)
                                    .atZone(ZoneId.of("Asia/Kolkata")).toInstant();
                        } catch (DateTimeException ignoredMinutes) {
                            // An unrecognized source timestamp must remain unavailable.
                        }
                    }
                }
            }
        }
        return null;
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
