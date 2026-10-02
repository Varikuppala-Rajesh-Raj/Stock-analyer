package com.tradingplatform.market.nse;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class NseFuturesContracts {
    private static final DateTimeFormatter NSE_EXPIRY =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private NseFuturesContracts() {}

    public static List<LocalDate> monthlyExpiries(JsonNode expiryDates, LocalDate today) {
        Map<YearMonth, LocalDate> lastExpiryByMonth = new TreeMap<>();
        if (expiryDates == null || !expiryDates.isArray()) return List.of();
        for (JsonNode value : expiryDates) {
            if (!value.isTextual()) continue;
            try {
                LocalDate expiry = LocalDate.parse(value.asText(), NSE_EXPIRY);
                if (!expiry.isBefore(today)) {
                    lastExpiryByMonth.merge(YearMonth.from(expiry), expiry,
                            (left, right) -> left.isAfter(right) ? left : right);
                }
            } catch (DateTimeParseException ignored) {
                // Ignore malformed entries from provider metadata.
            }
        }
        return new ArrayList<>(lastExpiryByMonth.values());
    }

    public static String identifier(LocalDate expiry) {
        return "FUTIDXNIFTY" + expiry.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + "XX0.00";
    }

    public static boolean isNiftyFutureQuote(JsonNode response, LocalDate expiry) {
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray() || data.isEmpty()) return false;
        JsonNode row = data.get(0);
        return "FUTIDX".equalsIgnoreCase(row.path("instrumentType").asText())
                && "NIFTY".equalsIgnoreCase(row.path("underlying").asText())
                && identifier(expiry).equalsIgnoreCase(row.path("identifier").asText())
                && expiry.format(NSE_EXPIRY).equalsIgnoreCase(row.path("expiryDate").asText())
                && row.path("lastPrice").isNumber();
    }
}