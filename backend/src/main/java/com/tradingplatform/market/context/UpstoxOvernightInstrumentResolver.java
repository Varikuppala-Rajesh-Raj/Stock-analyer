package com.tradingplatform.market.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.MarketDataUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UpstoxOvernightInstrumentResolver implements OvernightInstrumentResolver {

    private static final String USD_INR = "USD_INR";
    private static final String CRUDE_OIL = "CRUDE_OIL";
    private static final String GOLD = "GOLD";
    private static final Map<String, GlobalMasterInstrument> GLOBAL_INSTRUMENTS = Map.of(
            "GIFT_NIFTY", new GlobalMasterInstrument("GIFT NIFTY", "GLOBAL_INDEX"),
            "SP500", new GlobalMasterInstrument("S&P", "GLOBAL_INDEX"),
            "US_TECH_100", new GlobalMasterInstrument("US Tech 100", "GLOBAL_INDEX"),
            "DOW", new GlobalMasterInstrument("DOW JONES", "GLOBAL_INDEX"),
            "NIKKEI", new GlobalMasterInstrument("NIKKEI 225", "GLOBAL_INDEX"),
            "HANG_SENG", new GlobalMasterInstrument("HANG SENG", "GLOBAL_INDEX"),
            "FTSE_100", new GlobalMasterInstrument("FTSE 100", "GLOBAL_INDEX"),
            "DAX", new GlobalMasterInstrument("DAX", "GLOBAL_INDEX"),
            "BRENT", new GlobalMasterInstrument("Oil (Brent)", "GLOBAL_INDICATOR")
    );

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper json;
    private final String instrumentMasterUrl;
    private final String globalInstrumentMasterPath;
    private final String completeInstrumentMasterPath;

    public UpstoxOvernightInstrumentResolver(ObjectMapper json, String instrumentMasterUrl) {
        this(json, instrumentMasterUrl, "", "");
    }

    @Autowired
    public UpstoxOvernightInstrumentResolver(
            ObjectMapper json,
            @Value("${trading.upstox.overnight-instrument-master-url:"
                    + "https://assets.upstox.com/market-quote/instruments/exchange/complete.json.gz}")
                    String instrumentMasterUrl,
            @Value("${UPSTOX_GLOBAL_INSTRUMENT_MASTER_PATH:data/global.json.gz}")
                    String globalInstrumentMasterPath,
            @Value("${UPSTOX_COMPLETE_INSTRUMENT_MASTER_PATH:data/complete.json.gz}")
                    String completeInstrumentMasterPath
    ) {
        this.json = json;
        this.instrumentMasterUrl = instrumentMasterUrl;
        this.globalInstrumentMasterPath = globalInstrumentMasterPath;
        this.completeInstrumentMasterPath = completeInstrumentMasterPath;
    }

    @Override
    public List<ResolvedOvernightInstrument> resolve(
            String instrument,
            String configuredKey,
            String fallbackSegment
    ) {
        String code = instrument.toUpperCase(Locale.ROOT);
        GlobalMasterInstrument globalInstrument = GLOBAL_INSTRUMENTS.get(code);
        if (globalInstrument != null) {
            return resolveGlobalInstrument(globalInstrument, configuredKey, fallbackSegment);
        }

        String name;
        List<MarketSegment> marketSegments;
        switch (code) {
            case USD_INR -> {
                name = "USDINR";
                marketSegments = List.of(new MarketSegment("NCD_FO", "NSE"),
                        new MarketSegment("BCD_FO", "BSE"));
            }
            case CRUDE_OIL -> {
                name = "CRUDEOIL";
                marketSegments = List.of(new MarketSegment("NSE_COM", "NSE"));
            }
            case GOLD -> {
                name = "GOLD";
                marketSegments = List.of(new MarketSegment("NSE_COM", "NSE"));
            }
            default -> {
                if (configuredKey == null || configuredKey.isBlank()) {
                    return List.of(new ResolvedOvernightInstrument(null, "GLOBAL", fallbackSegment, null));
                }
                return List.of(new ResolvedOvernightInstrument(configuredKey, "GLOBAL", fallbackSegment, null));
            }
        }

        if (configuredKey != null && marketSegments.stream()
                .anyMatch(market -> configuredKey.startsWith(market.segment() + "|"))) {
            MarketSegment market = marketSegments.stream()
                    .filter(candidate -> configuredKey.startsWith(candidate.segment() + "|"))
                    .findFirst()
                    .orElseThrow();
            return List.of(new ResolvedOvernightInstrument(
                    configuredKey, market.exchange(), market.segment(), null));
        }

        JsonNode rows = readLocalMaster(completeInstrumentMasterPath);
        boolean localMasterAvailable = rows != null;
        if (rows == null) {
            rows = downloadRemoteInstrumentMaster();
        }
        if (rows == null || !rows.isArray()) {
            throw new MarketDataUnavailableException("Upstox instrument master response is not a JSON array.");
        }
        long now = Instant.now().toEpochMilli();
        List<ResolvedOvernightInstrument> resolved = matchingContracts(rows, name, marketSegments, now);
        if (resolved.isEmpty() && localMasterAvailable) {
            rows = downloadRemoteInstrumentMaster();
            if (rows == null || !rows.isArray()) {
                throw new MarketDataUnavailableException("Upstox instrument master response is not a JSON array.");
            }
            resolved = matchingContracts(rows, name, marketSegments, now);
        }

        if (resolved.isEmpty()) {
            throw new MarketDataUnavailableException(
                    "No active " + name + " futures contract was found in the Upstox instrument master.");
        }
        return List.copyOf(resolved);
    }

    private static List<ResolvedOvernightInstrument> matchingContracts(
            JsonNode rows,
            String name,
            List<MarketSegment> marketSegments,
            long now
    ) {
        List<ResolvedOvernightInstrument> resolved = new ArrayList<>();
        for (MarketSegment market : marketSegments) {
            List<JsonNode> matches = new ArrayList<>();
            for (JsonNode row : rows) {
                if (!name.equals(row.path("name").asText())
                        || !market.segment().equals(row.path("segment").asText())
                        || !market.exchange().equals(row.path("exchange").asText())
                        || !"FUT".equals(row.path("instrument_type").asText())) {
                    continue;
                }
                long expiry = row.path("expiry").asLong(-1);
                if (expiry >= now && !row.path("instrument_key").asText().isBlank()) {
                    matches.add(row);
                }
            }
            matches.stream()
                    .sorted(Comparator.comparingLong(row -> row.path("expiry").asLong(Long.MAX_VALUE)))
                    .limit(3)
                    .map(row -> new ResolvedOvernightInstrument(
                            row.path("instrument_key").asText(),
                            market.exchange(),
                            market.segment(),
                            row.path("trading_symbol").asText()))
                    .forEach(resolved::add);
        }
        return resolved;
    }

    private List<ResolvedOvernightInstrument> resolveGlobalInstrument(
            GlobalMasterInstrument instrument,
            String configuredKey,
            String fallbackSegment
    ) {
        JsonNode rows = readLocalMaster(globalInstrumentMasterPath);
        if (rows != null) {
            if (!rows.isArray()) {
                throw new MarketDataUnavailableException(
                        "Upstox global instrument master response is not a JSON array.");
            }
            for (JsonNode row : rows) {
                if (instrument.name().equalsIgnoreCase(row.path("name").asText())
                        && instrument.segment().equals(row.path("segment").asText())) {
                    String key = row.path("instrument_key").asText();
                    if (!key.isBlank()) {
                        return List.of(new ResolvedOvernightInstrument(
                                key,
                                row.path("exchange").asText("GLOBAL"),
                                row.path("segment").asText(),
                                row.path("trading_symbol").asText(null)));
                    }
                }
            }
        }
        if (configuredKey != null && !configuredKey.isBlank()) {
            return List.of(new ResolvedOvernightInstrument(
                    configuredKey, "GLOBAL", fallbackSegment, null));
        }
        return List.of(new ResolvedOvernightInstrument(null, "GLOBAL", fallbackSegment, null));
    }

    private JsonNode downloadRemoteInstrumentMaster() {
        if (instrumentMasterUrl == null || instrumentMasterUrl.isBlank()) {
            throw new MarketDataUnavailableException("Upstox instrument master URL is not configured.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(instrumentMasterUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new MarketDataUnavailableException(
                        "Upstox instrument master request failed with HTTP " + response.statusCode() + ".");
            }
            return parseMaster(response.body());
        } catch (MarketDataUnavailableException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new MarketDataUnavailableException("Upstox instrument master request was interrupted.", error);
        } catch (IOException | IllegalArgumentException error) {
            throw new MarketDataUnavailableException(
                    "Unable to read the Upstox instrument master: " + error.getMessage(), error);
        }
    }

    private JsonNode readLocalMaster(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(configuredPath);
            if (!path.isAbsolute() && !Files.isRegularFile(path)) {
                path = Path.of("..").resolve(path).normalize();
            }
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return parseMaster(Files.readAllBytes(path));
        } catch (IOException | IllegalArgumentException error) {
            throw new MarketDataUnavailableException(
                    "Unable to read the Upstox instrument master file " + configuredPath + ": "
                            + error.getMessage(), error);
        }
    }

    private JsonNode parseMaster(byte[] body) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(body)) {
            if (isGzip(body)) {
                try (GZIPInputStream gzip = new GZIPInputStream(input)) {
                    JsonNode master = json.readTree(gzip);
                    if (master == null) {
                        throw new IOException("Instrument master is empty.");
                    }
                    return master;
                }
            }
            JsonNode master = json.readTree(body);
            if (master == null) {
                throw new IOException("Instrument master is empty.");
            }
            return master;
        }
    }

    private static boolean isGzip(byte[] body) {
        return body.length > 1 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b;
    }

    private record MarketSegment(String segment, String exchange) {
    }

    private record GlobalMasterInstrument(String name, String segment) {
    }
}
