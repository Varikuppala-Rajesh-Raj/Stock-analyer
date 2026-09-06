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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Upstox V3 REST adapter.
 *
 * Raw Upstox vendor JSON is contained inside this class.
 */
@Component
public class UpstoxMarketDataProvider implements MarketDataProvider {

    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private final ObjectMapper json;
    private final String baseUrl;
    private final String token;

    public UpstoxMarketDataProvider(
            ObjectMapper json,
            @Value("${trading.upstox.UPSTOX_BASE_URL}") String baseUrl,
            @Value("${trading.upstox.UPSTOX_ACCESS_TOKEN}") String token
    ) {
        this.json = json;
        this.baseUrl = baseUrl;
        this.token = token;

        System.out.println("========== UPSTOX REST CONFIG ==========");
        System.out.println("Base URL       : " + baseUrl);
        System.out.println(
                "Token present  : " +
                (token != null && !token.isBlank())
        );
        System.out.println(
                "Token length   : " +
                (token == null ? 0 : token.length())
        );
        System.out.println("=========================================");
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
            throw new MarketDataUnavailableException(
                    "Upstox access token is not configured."
            );
        }

        String url = baseUrl + path;

        try {

            System.out.println();
            System.out.println("========== UPSTOX REST REQUEST ==========");
            System.out.println("URL           : " + url);
            System.out.println("Token present : " + !token.isBlank());
            System.out.println("Token length  : " + token.length());
            System.out.println("=========================================");

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

            System.out.println();
            System.out.println("========== UPSTOX REST RESPONSE ==========");
            System.out.println(
                    "HTTP STATUS : " +
                    response.statusCode()
            );
            System.out.println(
                    "BODY        : " +
                    response.body()
            );
            System.out.println("==========================================");

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
            return json.readTree(response.body());

        } catch (MarketDataUnavailableException e) {

            throw e;

        } catch (Exception e) {

            e.printStackTrace();

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

        result.sort(
                Comparator.comparing(
                        Candle::timestamp
                )
        );

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

    private static Instant timestamp(
            JsonNode node
    ) {

        try {

            return node.isNumber()
                    ? Instant.ofEpochMilli(
                            node.asLong()
                    )
                    : Instant.parse(
                            node.asText()
                    );

        } catch (Exception e) {

            return Instant.now();
        }
    }
}