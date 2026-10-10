package com.tradingplatform.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upstox V3 REST adapter.
 *
 * Raw Upstox vendor JSON is contained inside this class.
 */
@Component
public class UpstoxMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(UpstoxMarketDataProvider.class);
    private static final String OVERNIGHT_QUOTES_PATH = "/v3/market-quote/quotes?instrument_key=";
    private static final String OVERNIGHT_INDICATOR_LTP_PATH = "/v2/market-quote/ltp?instrument_key=";
    private static final String OVERNIGHT_INDICATOR_SYMBOL_LTP_PATH = "/v2/market-quote/ltp?symbol=";

    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private final ObjectMapper json;
    private final String baseUrl;
    private final String token;

    public UpstoxMarketDataProvider(
            ObjectMapper json,
            @Value("${trading.upstox.UPSTOX_BASE_URL:http://localhost}") String baseUrl,
            @Value("${trading.upstox.UPSTOX_ACCESS_TOKEN:dummy-token}") String token
    ) {
        this.json = json;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    @Override
    public Quote getQuote(Instrument instrument) {

        JsonNode data = get(
                "/v3/market-quote/ohlc?instrument_key="
                        + encoded(instrument.instrumentKey())
                        + "&interval=1d"
        ).path("data");

        JsonNode item =
                data.fields().hasNext()
                        ? data.fields().next().getValue()
                        : null;

        if (item == null) {
            throw new MarketDataUnavailableException(
                    "Upstox returned no quote for "
                            + instrument.symbol()
            );
        }

        JsonNode live = item.path("live_ohlc");
        JsonNode previous = item.path("prev_ohlc");

        BigDecimal price =
                decimal(item, "last_price");

        BigDecimal previousClose =
                decimal(previous, "close");

        BigDecimal change =
                price.subtract(previousClose);

        BigDecimal changePercent =
                previousClose.signum() == 0
                        ? BigDecimal.ZERO
                        : change
                                .multiply(BigDecimal.valueOf(100))
                                .divide(
                                        previousClose,
                                        4,
                                        java.math.RoundingMode.HALF_UP
                                );

        return new Quote(
                instrument.symbol(),
                instrument.instrumentKey(),
                price,
                decimal(live, "open"),
                decimal(live, "high"),
                decimal(live, "low"),
                previousClose,
                change,
                changePercent,
                decimal(live, "volume"),
                timestamp(live.path("ts"))
        );
    }

    public Quote getOvernightQuote(Instrument instrument) {
        if ("GLOBAL_INDICATOR".equals(instrument.segment())) {
            return getOvernightIndicatorLtp(instrument);
        }
        String path = OVERNIGHT_QUOTES_PATH + encoded(instrument.instrumentKey());
        log.info("[OVERNIGHT_UPSTOX_REQUEST] instrumentKey={} requestPath={} tokenPresent={}",
                instrument.instrumentKey(), path,
                token != null && !token.isBlank());
        JsonNode response = get(path);
        JsonNode data = response.path("data");
        JsonNode item = quoteForInstrument(data, instrument.instrumentKey());
        if (item.isMissingNode() || item.isNull()) {
            log.warn("[OVERNIGHT_PARSE] instrumentKey={} parsed=false reason=instrument_not_found",
                    instrument.instrumentKey());
            throw new NoUsableMarketQuoteException(
                    "Upstox returned no overnight quote for " + instrument.instrumentKey() + ".");
        }

        JsonNode ohlc = item.path("ohlc");
        BigDecimal lastPrice = optionalDecimal(item, "last_price");
        BigDecimal previousClose = optionalDecimal(ohlc, "close");
        BigDecimal netChange = optionalDecimal(item, "net_change");
        Instant providerTimestamp = firstValidTimestamp(
                ohlc.path("timestamp"),
                item.path("timestamp"),
                item.path("last_trade_time")
        );
        boolean domesticFuture = isDomesticFuture(instrument);

        if (lastPrice == null || lastPrice.signum() <= 0
                || (!domesticFuture && (netChange == null || providerTimestamp == null))) {
            log.warn("[OVERNIGHT_PARSE] instrumentKey={} parsed=false lastPrice={} netChange={} "
                            + "close={} providerTimestamp={} reason=required_quote_field_missing_or_invalid "
                            + "domesticFuture={}",
                    instrument.instrumentKey(), lastPrice, netChange, previousClose, providerTimestamp,
                    domesticFuture);
            throw new NoUsableMarketQuoteException(
                    "Upstox returned an invalid overnight quote for " + instrument.instrumentKey()
                            + " (a valid price is required; global-index quotes also require net change "
                            + "and provider timestamp).");
        }

        BigDecimal changePercent = previousClose == null || previousClose.signum() <= 0
                ? null
                : netChange.multiply(BigDecimal.valueOf(100))
                        .divide(previousClose, 6, java.math.RoundingMode.HALF_UP);
        log.info("[OVERNIGHT_PARSE] instrumentKey={} parsed=true lastPrice={} netChange={} "
                        + "close={} providerTimestamp={}",
                instrument.instrumentKey(), lastPrice, netChange, previousClose, providerTimestamp);
        return new Quote(
                instrument.symbol(),
                instrument.instrumentKey(),
                lastPrice,
                optionalDecimal(ohlc, "open"),
                optionalDecimal(ohlc, "high"),
                optionalDecimal(ohlc, "low"),
                previousClose,
                netChange,
                changePercent,
                optionalDecimal(item, "volume"),
                providerTimestamp
        );
    }

    private Quote getOvernightIndicatorLtp(Instrument instrument) {
        String path = OVERNIGHT_INDICATOR_LTP_PATH + encoded(instrument.instrumentKey());
        log.info("[OVERNIGHT_UPSTOX_REQUEST] instrumentKey={} requestPath={} tokenPresent={}",
                instrument.instrumentKey(), path, token != null && !token.isBlank());
        JsonNode response;
        try {
            response = get(path);
        } catch (MarketDataUnavailableException error) {
            if (!instrument.instrumentKey().equals("GLOBAL_INDICATOR|BZUSD")
                    || error.getMessage() == null || !error.getMessage().contains("UDAPI1087")) {
                throw error;
            }
            String symbol = instrument.instrumentKey().substring(instrument.instrumentKey().indexOf('|') + 1);
            String symbolPath = OVERNIGHT_INDICATOR_SYMBOL_LTP_PATH + encoded(symbol);
            log.info("[OVERNIGHT_UPSTOX_FALLBACK] instrumentKey={} fallbackPath={} reason=invalid_instrument_key",
                    instrument.instrumentKey(), symbolPath);
            try {
                response = get(symbolPath);
            } catch (MarketDataUnavailableException fallbackError) {
                throw new MarketDataUnavailableException(
                        "Upstox rejected Brent using both instrument_key and symbol requests. "
                                + "instrument_key response: " + error.getMessage()
                                + " symbol response: " + fallbackError.getMessage(),
                        fallbackError);
            }
        }
        JsonNode item = quoteForInstrument(response.path("data"), instrument.instrumentKey());
        if (item.isMissingNode()) {
            item = response.path("data").path(instrument.instrumentKey().substring(
                    instrument.instrumentKey().indexOf('|') + 1));
        }
        if (item.isMissingNode() && response.path("data").isObject()
                && response.path("data").fields().hasNext()) {
            item = response.path("data").fields().next().getValue();
        }
        BigDecimal lastPrice = optionalDecimal(item, "last_price");
        if (lastPrice == null || lastPrice.signum() <= 0) {
            throw new NoUsableMarketQuoteException(
                    "Upstox returned no usable LTP for global indicator " + instrument.instrumentKey() + ".");
        }
        log.info("[OVERNIGHT_PARSE] instrumentKey={} parsed=true lastPrice={} "
                        + "providerTimestamp=unavailable quoteType=LTP_ONLY",
                instrument.instrumentKey(), lastPrice);
        return new Quote(instrument.symbol(), instrument.instrumentKey(), lastPrice,
                null, null, null, null, null, null, null, null);
    }

    private static boolean isDomesticFuture(Instrument instrument) {
        return "NSE".equals(instrument.exchange()) || "BSE".equals(instrument.exchange());
    }

    @Override
    public List<Candle> getHistoricalCandles(
            String key,
            Timeframe timeframe,
            LocalDate from,
            LocalDate to
    ) {

        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "from must be on or before to"
            );
        }

        String path =
                "/v3/historical-candle/"
                        + encoded(key)
                        + "/"
                        + timeframe.unit()
                        + "/"
                        + timeframe.interval()
                        + "/"
                        + to
                        + "/"
                        + from;

        System.out.println("========== HISTORICAL REQUEST ==========");
        System.out.println("Instrument : " + key);
        System.out.println("Timeframe  : " + timeframe);
        System.out.println("From       : " + from);
        System.out.println("To         : " + to);
        System.out.println("Path       : " + path);
        System.out.println("========================================");

        return candles(get(path));
    }

    @Override
    public List<Candle> getIntradayCandles(
            String key,
            Timeframe timeframe
    ) {

        if (!timeframe.supportsIntraday()) {
            throw new IllegalArgumentException(
                    "Timeframe is not supported for intraday: "
                            + timeframe
            );
        }

        String path =
                "/v3/historical-candle/intraday/"
                        + encoded(key)
                        + "/"
                        + timeframe.unit()
                        + "/"
                        + timeframe.interval();

        System.out.println("========== INTRADAY REQUEST ==========");
        System.out.println("Instrument : " + key);
        System.out.println("Timeframe  : " + timeframe);
        System.out.println("Path       : " + path);
        System.out.println("======================================");

        return candles(get(path));
    }

    /**
     * Executes a GET request against Upstox.
     */
    private JsonNode get(String path) {

        if (token == null || token.isBlank()) {
            if (path.startsWith(OVERNIGHT_QUOTES_PATH)) {
                log.error("[OVERNIGHT_UPSTOX_RESPONSE] instrumentKey={} success=false httpStatus=NONE "
                                + "responseReceived=false tokenPresent=false reason=access_token_missing",
                        instrumentFromQuotesPath(path));
            }
            throw new MarketDataUnavailableException(
                    "Upstox access token is not configured."
            );
        }

        String url = baseUrl + path;
        boolean overnightRequest = path.startsWith(OVERNIGHT_QUOTES_PATH)
                || path.startsWith(OVERNIGHT_INDICATOR_LTP_PATH)
                || path.startsWith(OVERNIGHT_INDICATOR_SYMBOL_LTP_PATH);
        int responseStatus = -1;
        boolean responseReceived = false;

        try {


            HttpRequest request =
                    HttpRequest.newBuilder(
                            URI.create(url)
                    )
                    .timeout(Duration.ofSeconds(10))
                    .header(
                            "Accept",
                            "application/json"
                    )
                    .header(
                            "Authorization",
                            "Bearer " + token
                    )
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );
            responseStatus = response.statusCode();
            responseReceived = response.body() != null;
            if (overnightRequest) {
                log.info("[OVERNIGHT_UPSTOX_RESPONSE] instrumentKey={} success={} httpStatus={} "
                                + "responseReceived={} tokenPresent={}",
                        instrumentFromQuotesPath(path), response.statusCode() / 100 == 2,
                        response.statusCode(), response.body() != null, true);
            }

                  /*
             * Authentication failure
             */
            if (response.statusCode() == 401) {

                throw new MarketDataUnavailableException(
                        "Upstox rejected the configured access token. "
                                + "HTTP 401. Response: "
                                + response.body()
                );
            }

            /*
             * Rate limit
             */
            if (response.statusCode() == 429) {

                throw new MarketDataUnavailableException(
                        "Upstox rate limit reached. "
                                + "HTTP 429. Response: "
                                + response.body()
                );
            }

            /*
             * Any other non-success response
             */
            if (response.statusCode() / 100 != 2) {

                throw new MarketDataUnavailableException(
                        "Upstox request failed with HTTP "
                                + response.statusCode()
                                + ". Response: "
                                + response.body()
                );
            }

            /*
             * Successful response.
             */
            JsonNode parsed = json.readTree(response.body());
            if (overnightRequest) {
                log.info("[OVERNIGHT_UPSTOX_RESPONSE] instrumentKey={} jsonParsed=true dataPresent={}",
                        instrumentFromQuotesPath(path), parsed.hasNonNull("data"));
            }
            return parsed;

        } catch (MarketDataUnavailableException e) {

            if (overnightRequest && token != null && !token.isBlank()) {
                log.error("[OVERNIGHT_UPSTOX_RESPONSE] instrumentKey={} success=false httpStatus={} "
                                + "responseReceived={} tokenPresent=true reason={}",
                        instrumentFromQuotesPath(path),
                        responseStatus < 0 ? httpStatus(e.getMessage()) : responseStatus,
                        responseReceived || hasHttpStatus(e.getMessage()),
                        safeLogMessage(e.getMessage()));
            }
            throw e;

        } catch (Exception e) {

            if (overnightRequest) {
                log.error("[OVERNIGHT_UPSTOX_RESPONSE] instrumentKey={} success=false httpStatus={} "
                                + "responseReceived={} tokenPresent={} reason={}",
                        instrumentFromQuotesPath(path), responseStatus < 0 ? "NONE" : responseStatus,
                        responseReceived, token != null && !token.isBlank(),
                        safeLogMessage(e.getMessage()), e);
            } else {
                log.error("Upstox request failed for path {}: {}", path, e.getMessage(), e);
            }

            throw new MarketDataUnavailableException(
                    "Upstox request failed: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Converts Upstox candle JSON into local Candle objects.
     *
     * Expected Upstox candle structure:
     *
     * [
     *   timestamp,
     *   open,
     *   high,
     *   low,
     *   close,
     *   volume
     * ]
     */
    private List<Candle> candles(JsonNode response) {

        List<Candle> result = new ArrayList<>();

        JsonNode candles =
                response
                        .path("data")
                        .path("candles");

        System.out.println();
        System.out.println("========== CANDLE RESPONSE ==========");
        System.out.println(
                "Candle count: " + candles.size()
        );
        System.out.println("=====================================");

        for (JsonNode row : candles) {

            try {

                result.add(
                        new Candle(
                                Instant.parse(
                                        row.get(0).asText()
                                ),
                                new BigDecimal(
                                        row.get(1).asText()
                                ),
                                new BigDecimal(
                                        row.get(2).asText()
                                ),
                                new BigDecimal(
                                        row.get(3).asText()
                                ),
                                new BigDecimal(
                                        row.get(4).asText()
                                ),
                                new BigDecimal(
                                        row.get(5).asText()
                                )
                        )
                );

            } catch (Exception e) {

                throw new MarketDataUnavailableException(
                        "Upstox returned an invalid candle: "
                                + row,
                        e
                );
            }
        }

        result = new ArrayList<>(MarketDataNormalizer.normalizeCandles(result));

        if (result.isEmpty()) {

            throw new MarketDataUnavailableException(
                    "Upstox returned no candles."
            );
        }

        return result;
    }

    private static String encoded(String value) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        ).replace("+", "%20");
        
    }

    private static JsonNode quoteForInstrument(JsonNode data, String instrumentKey) {
        JsonNode exactMatch = data.path(instrumentKey);
        if (!exactMatch.isMissingNode() && !exactMatch.isNull()) {
            return exactMatch;
        }
        if (data.isObject()) {
            var fields = data.fields();
            while (fields.hasNext()) {
                JsonNode quote = fields.next().getValue();
                if (instrumentKey.equals(quote.path("instrument_token").asText())) {
                    return quote;
                }
            }
        }
        return data.path("__missing_instrument_quote__");
    }

    private static String instrumentFromQuotesPath(String path) {
        int marker = path.indexOf("instrument_key=");
        boolean symbolParameter = false;
        if (marker < 0) {
            marker = path.indexOf("symbol=");
            symbolParameter = marker >= 0;
        }
        if (marker < 0) {
            return "UNKNOWN";
        }
        String parameter = symbolParameter ? "symbol=" : "instrument_key=";
        String encodedKey = path.substring(marker + parameter.length());
        try {
            String decoded = java.net.URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
            return symbolParameter ? "GLOBAL_INDICATOR|" + decoded : decoded;
        } catch (IllegalArgumentException error) {
            return encodedKey;
        }
    }

    private static String httpStatus(String message) {
        if (message == null) {
            return "UNKNOWN";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("HTTP\\s+(\\d{3})")
                .matcher(message);
        return matcher.find() ? matcher.group(1) : "UNKNOWN";
    }

    private static boolean hasHttpStatus(String message) {
        return !"UNKNOWN".equals(httpStatus(message));
    }

    private static String safeLogMessage(String message) {
        if (message == null || message.isBlank()) {
            return "unknown_error";
        }
        String safe = message.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer [REDACTED]");
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }

    private static BigDecimal decimal(
            JsonNode node,
            String field
    ) {

        if (node == null
                || node.path(field).isMissingNode()
                || node.path(field).isNull()) {

            return BigDecimal.ZERO;
        }

        return new BigDecimal(
                node.path(field).asText()
        );
    }

    private static BigDecimal optionalDecimal(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()
                || node.path(field).asText().isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(node.path(field).asText());
            return value;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static Instant firstValidTimestamp(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            try {
                if (node == null || node.isMissingNode() || node.isNull()) {
                    continue;
                }
                Instant parsed = node.isNumber()
                        ? Instant.ofEpochMilli(node.asLong())
                        : parseProviderTimestamp(node.asText());
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // Try the next provider timestamp field.
            }
        }
        return null;
    }

    private static Instant timestamp(JsonNode node) {
        return firstValidTimestamp(node);
    }

    private static Instant parseProviderTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return OffsetDateTime.parse(value).toInstant();
        }
    }
}