package com.tradingplatform.market.nse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradingplatform.persistence.NseReferenceSnapshotEntity;
import com.tradingplatform.persistence.NseReferenceSnapshotRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class NseReferenceSnapshotService {
    private static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter NSE_TIMESTAMP =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm[:ss]", Locale.ENGLISH);
    private static final DateTimeFormatter NSE_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final Set<String> FII_DII_CATEGORIES = Set.of("DII", "FII/FPI");

    private final NseReferenceSnapshotRepository snapshots;
    private final ObjectMapper objectMapper;

    public NseReferenceSnapshotService(
            NseReferenceSnapshotRepository snapshots,
            ObjectMapper objectMapper
    ) {
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JsonNode persistIndiaVix(JsonNode row) {
        if (!"INDIA VIX".equalsIgnoreCase(firstText(row, "index", "indexSymbol"))) {
            throw invalidProviderData("NSE response did not contain a valid India VIX row.");
        }
        for (String field : new String[]{"last", "variation", "percentChange", "open", "high", "low", "previousClose"}) {
            requiredNumber(row, field);
        }

        ObjectNode normalized = row.deepCopy();
        normalized.put("symbol", "INDIA VIX");
        normalized.put("change", requiredNumber(row, "variation"));
        persist("INDIA_VIX", normalized, providerTimestamp(row), null);
        return normalized;
    }

    @Transactional
    public JsonNode persistFiiDii(JsonNode payload) {
        JsonNode records = payload.path("value");
        if (!records.isArray()) {
            throw invalidProviderData("NSE FII/DII response did not contain a value array.");
        }
        Set<String> categoriesFound = new java.util.HashSet<>();
        LocalDate reportDate = null;
        for (JsonNode record : records) {
            String category = firstText(record, "category");
            if (!FII_DII_CATEGORIES.contains(category)) continue;
            categoriesFound.add(category);
            requiredNumber(record, "buyValue");
            requiredNumber(record, "sellValue");
            requiredNumber(record, "netValue");
            LocalDate rowDate = providerDate(record, "date");
            if (reportDate == null) reportDate = rowDate;
        }
        if (!categoriesFound.containsAll(FII_DII_CATEGORIES)) {
            throw invalidProviderData("NSE FII/DII response is missing FII/FPI or DII data.");
        }
        persist("FII_DII", payload, null, reportDate);
        return payload;
    }

    private void persist(String dataset, JsonNode payload, Instant providerTimestamp, LocalDate providerDate) {
        NseReferenceSnapshotEntity snapshot = new NseReferenceSnapshotEntity();
        snapshot.datasetKey = dataset;
        snapshot.providerTimestamp = providerTimestamp;
        snapshot.providerDate = providerDate;
        snapshot.retrievedAt = Instant.now();
        try {
            snapshot.payloadJson = objectMapper.writeValueAsString(payload);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to serialize validated NSE market data.", error);
        }
        snapshots.save(snapshot);
    }

    private static Instant providerTimestamp(JsonNode row) {
        String timestamp = firstText(row, "timestamp", "timeVal", "lastUpdateTime");
        if (timestamp == null) return null;
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(timestamp, NSE_TIMESTAMP).atZone(NSE_ZONE).toInstant();
            } catch (DateTimeParseException error) {
                throw invalidProviderData("NSE India VIX timestamp has an unsupported format.");
            }
        }
    }

    private static LocalDate providerDate(JsonNode row, String field) {
        String date = firstText(row, field);
        if (date == null) return null;
        try {
            return LocalDate.parse(date, NSE_DATE);
        } catch (DateTimeParseException error) {
            throw invalidProviderData("NSE report date has an unsupported format.");
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static double requiredNumber(JsonNode node, String field) {
        JsonNode value = node.path(field);
        try {
            double number = value.isNumber() ? value.doubleValue() : Double.parseDouble(value.asText());
            if (Double.isFinite(number)) return number;
        } catch (NumberFormatException ignored) {
            // Report invalid upstream data below.
        }
        throw invalidProviderData("NSE response contains an invalid numeric field: " + field + ".");
    }

    private static ResponseStatusException invalidProviderData(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }
}
