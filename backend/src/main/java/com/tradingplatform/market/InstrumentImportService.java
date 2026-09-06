package com.tradingplatform.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.persistence.InstrumentCatalogRepository;
import com.tradingplatform.persistence.InstrumentEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

@Service
public class InstrumentImportService {

    private final InstrumentCatalogRepository repository;
    private final ObjectMapper json;
    private final String source;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public InstrumentImportService(
            InstrumentCatalogRepository repository,
            ObjectMapper json,
            @Value("${UPSTOX_INSTRUMENT_MASTER_URL:}") String source) {

        this.repository = repository;
        this.json = json;
        this.source = source;
    }

    /**
     * Downloads and imports the configured Upstox instrument master.
     *
     * Supports:
     *
     *     .json
     *     .json.gz
     */
    @Transactional
    public InstrumentImportReport importConfiguredSource() {

        if (source == null || source.isBlank()) {
            throw new IllegalStateException(
                    "UPSTOX_INSTRUMENT_MASTER_URL is not configured"
            );
        }

        try {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(source))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<byte[]> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofByteArray()
                    );

            if (response.statusCode() / 100 != 2) {

                throw new IllegalStateException(
                        "Instrument master download failed: HTTP "
                                + response.statusCode()
                );
            }

            byte[] body = response.body();

            JsonNode root;

            if (isGzip(body) || source.toLowerCase(Locale.ROOT).endsWith(".gz")) {

                try (InputStream gzip =
                             new GZIPInputStream(
                                     new ByteArrayInputStream(body)
                             )) {

                    root = json.readTree(gzip);
                }

            } else {

                root = json.readTree(body);
            }

            return importJson(root);

        } catch (IllegalStateException e) {

            throw e;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Instrument master import failed",
                    e
            );
        }
    }

    /**
     * Imports instrument-master JSON.
     *
     * The official instrument master is expected to contain
     * an array of instrument objects.
     */
    @Transactional
    public InstrumentImportReport importJson(JsonNode rows) {

        if (rows == null || rows.isNull()) {

            throw new IllegalArgumentException(
                    "Instrument master response is empty"
            );
        }

        /*
         * Allow a single JSON object as well as an array.
         */
        if (rows.isObject()) {

            rows = json.createArrayNode()
                    .add(rows);
        }

        if (!rows.isArray()) {

            throw new IllegalArgumentException(
                    "Official instrument master must be a JSON array"
            );
        }

        int total = 0;
        int valid = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;

        for (JsonNode row : rows) {

            total++;

            try {

                String key =
                        text(row, "instrument_key");

                String segment =
                        text(row, "segment");

                String exchange =
                        text(row, "exchange");

                String type =
                        text(row, "instrument_type");

                String symbol =
                        text(row, "trading_symbol");

                /*
                 * Required fields.
                 */
                if (key.isBlank()
                        || segment.isBlank()
                        || exchange.isBlank()
                        || type.isBlank()
                        || symbol.isBlank()) {

                    skipped++;
                    continue;
                }

                /*
                 * We only import the instruments currently
                 * required by the trading platform:
                 *
                 * NSE equity
                 * BSE equity
                 * NSE indices
                 */
                boolean supported =

                        (
                                (segment.equals("NSE_EQ")
                                        || segment.equals("BSE_EQ"))
                                        &&
                                (type.equals("EQ")
                                        || type.equals("BE"))
                        )

                        ||

                        (
                                segment.equals("NSE_INDEX")
                                        &&
                                type.equals("INDEX")
                        );

                if (!supported) {

                    skipped++;
                    continue;
                }

                valid++;

                boolean exists =
                        repository.existsById(key);

                InstrumentEntity entity =
                        repository.findById(key)
                                .orElseGet(
                                        InstrumentEntity::new
                                );

                entity.instrumentKey = key;

                entity.segment = segment;

                entity.exchange = exchange;

                entity.instrumentType = type;

                /*
                 * Application-level symbol.
                 *
                 * Example:
                 *
                 * NIFTY
                 *
                 * while the actual Upstox key is:
                 *
                 * NSE_INDEX|Nifty 50
                 */
                entity.symbol =
                        symbol
                                .trim()
                                .toUpperCase(Locale.ROOT);

                /*
                 * Preserve the original Upstox trading symbol.
                 */
                entity.tradingSymbol =
                        symbol;

                entity.companyName =
                        text(row, "name");

                entity.isin =
                        text(row, "isin");

                entity.active =
                        true;

                entity.lastUpdated =
                        Instant.now();

                repository.save(entity);

                if (exists) {

                    updated++;

                } else {

                    inserted++;
                }

            } catch (Exception ignored) {

                failed++;
            }
        }

        return new InstrumentImportReport(
                total,
                valid,
                inserted,
                updated,
                skipped,
                failed
        );
    }

    /**
     * Extract a textual JSON property safely.
     */
    private static String text(
            JsonNode node,
            String name) {

        JsonNode value =
                node.path(name);

        return value.isTextual()
                ? value.asText().trim()
                : "";
    }

    /**
     * Detect gzip using the standard gzip magic bytes.
     *
     * GZIP starts with:
     *
     *     0x1F 0x8B
     */
    private static boolean isGzip(byte[] data) {

        return data != null
                && data.length >= 2
                && (data[0] & 0xFF) == 0x1F
                && (data[1] & 0xFF) == 0x8B;
    }
}