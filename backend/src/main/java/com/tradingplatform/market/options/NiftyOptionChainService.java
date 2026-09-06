package com.tradingplatform.market.options;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import com.tradingplatform.persistence.OptionMarketSnapshotEntity;
import com.tradingplatform.persistence.OptionMarketSnapshotRepository;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.List;
import java.util.Objects;

@Service
public class NiftyOptionChainService {

    private static final String NIFTY_INDEX =
            "NSE_INDEX|Nifty 50";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
        private final OptionMarketSnapshotRepository snapshots;

    public NiftyOptionChainService(
            ObjectMapper objectMapper,
            OptionMarketSnapshotRepository snapshots,
            @Value("${trading.upstox.base-url:https://api.upstox.com}")
            String baseUrl,
            @Value("${upstox.access-token}")
            String accessToken
    ) {

        this.objectMapper = objectMapper;
        this.snapshots = snapshots;

        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader(
                                "Authorization",
                                "Bearer " + accessToken
                        )
                        .defaultHeader(
                                "Accept",
                                MediaType.APPLICATION_JSON_VALUE
                        )
                        .build();
    }

    public JsonNode getNiftyOptionChain(
            String expiry
    ) {

        if (expiry == null || expiry.isBlank()) {
            throw new IllegalArgumentException(
                    "Expiry is required."
            );
        }

        try {

            JsonNode result = restClient
                    .get()
                    .uri(uriBuilder ->
                            uriBuilder
                                    .path("/v2/option/chain")
                                    .queryParam(
                                            "instrument_key",
                                            NIFTY_INDEX
                                    )
                                    .queryParam(
                                            "expiry_date",
                                            expiry
                                    )
                                    .build()
                    )
                    .retrieve()
                    .body(JsonNode.class);
                if (result != null && result.path("data").isArray() && !result.path("data").isEmpty()) {
                        return result;
                }
                return historicalFallback(expiry, null);

                } catch (RestClientException e) {
                        return historicalFallback(expiry, e);
        }
    }

        private JsonNode historicalFallback(String expiry, Exception cause) {
                LocalDate requestedExpiry = LocalDate.parse(expiry);
                var ordered = snapshots.findBySymbolAndExpiryOrderByTimestampDesc("NIFTY", requestedExpiry);
                if (ordered.isEmpty()) {
                    IllegalStateException failure = new IllegalStateException(
                            "Upstox option-chain request failed and no historical NIFTY option snapshots are available for expiry " + expiry
                    );
                            if (cause != null) failure.initCause(cause);
                            throw failure;
                }

                Instant latestTimestamp = ordered.get(0).timestamp;
                var latest = ordered.stream()
                                .filter(snapshot -> latestTimestamp.equals(snapshot.timestamp))
                                .toList();
                var byStrike = new java.util.LinkedHashMap<String, com.fasterxml.jackson.databind.node.ObjectNode>();
                for (OptionMarketSnapshotEntity snapshot : latest) {
                        String strike = snapshot.strikePrice.stripTrailingZeros().toPlainString();
                        var row = byStrike.computeIfAbsent(strike, ignored -> {
                                var created = objectMapper.createObjectNode();
                                created.put("strike_price", snapshot.strikePrice);
                                created.put("underlying_spot_price", snapshot.underlyingSpot);
                                return created;
                        });
                        var option = objectMapper.createObjectNode();
                        option.put("instrument_key", snapshot.instrumentKey);
                        var market = option.putObject("market_data");
                        put(market, "ltp", snapshot.ltp);
                        put(market, "bid_price", snapshot.bid);
                        put(market, "ask_price", snapshot.ask);
                        put(market, "volume", snapshot.volume);
                        put(market, "oi", snapshot.oi);
                        var greeks = option.putObject("option_greeks");
                        put(greeks, "iv", snapshot.iv);
                        put(greeks, "delta", snapshot.delta);
                        put(greeks, "gamma", snapshot.gamma);
                        put(greeks, "theta", snapshot.theta);
                        put(greeks, "vega", snapshot.vega);
                        row.set("CE".equals(snapshot.optionType) ? "call_options" : "put_options", option);
                }
                var data = objectMapper.createArrayNode();
                byStrike.values().forEach(data::add);
                var response = objectMapper.createObjectNode();
                response.put("status", "success");
                response.set("data", data);
                response.put("dataSource", "HISTORICAL_SNAPSHOT");
                response.put("snapshotTimestamp", latestTimestamp.toString());
                return response;
        }

        private static void put(com.fasterxml.jackson.databind.node.ObjectNode node, String name, java.math.BigDecimal value) {
                if (value != null) node.put(name, value);
        }

    /** Discovers listed NIFTY expiry dates from Upstox rather than embedding a calendar in code. */
    public Optional<String> nearestNiftyExpiry() {
        try {
            JsonNode data = restClient.get().uri(uriBuilder -> uriBuilder.path("/v2/option/contract").queryParam("instrument_key", NIFTY_INDEX).build()).retrieve().body(JsonNode.class).path("data");
            return java.util.stream.StreamSupport.stream(data.spliterator(), false)
                    .map(node -> node.path("expiry").asText(node.path("expiry_date").asText("")))
                    .filter(value -> !value.isBlank())
                    .filter(value -> { try { return !LocalDate.parse(value).isBefore(LocalDate.now()); } catch (Exception ignored) { return false; } })
                    .min(Comparator.comparing(LocalDate::parse));
        } catch (RestClientResponseException exception) {
            return Optional.empty();
        }
    }

public List<String> niftyExpiries(int limit) {
    try {
        JsonNode data = restClient
                .get()
                .uri(uriBuilder ->
                        uriBuilder
                                .path("/v2/option/contract")
                                .queryParam("instrument_key", NIFTY_INDEX)
                                .build()
                )
                .retrieve()
                .body(JsonNode.class)
                .path("data");

        int safeLimit = Math.min(Math.max(limit, 1), 10);
        LocalDate today = LocalDate.now();

        return java.util.stream.StreamSupport
                .stream(data.spliterator(), false)
                .map(node -> node.path("expiry")
                        .asText(node.path("expiry_date").asText("")))
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return LocalDate.parse(value);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(date -> !date.isBefore(today))
                .distinct()
                .sorted()
                .limit(safeLimit)
                .map(LocalDate::toString)
                .toList();

    } catch (RestClientException e) {
        return List.of();
    }
}

}
