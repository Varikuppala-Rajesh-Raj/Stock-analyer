package com.tradingplatform.market.nse;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.controller.NseMarketDataController;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.FuturesContext;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.Metric;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.NiftyContext;
import com.tradingplatform.market.nse.NseMarketContextSnapshot.Status;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NseMarketContextService {
    private static final Logger log = LoggerFactory.getLogger(NseMarketContextService.class);
    private static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter NSE_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("dd-MMM-yyyy HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .toFormatter(Locale.ENGLISH);

    private final NseMarketDataController nse;
    private final NseFuturesObservationService futuresObservations;
    private final Duration staleAfter;
    private final Duration windowTolerance;
    private final int maxObservations;
    private final NavigableMap<Instant, SpotObservation> spotHistory = new TreeMap<>();
    private final NavigableMap<Instant, NseFuturesObservation> futuresHistory = new TreeMap<>();
    private String spotFailure;
    private String futuresFailure;

    public NseMarketContextService(
            NseMarketDataController nse,
            NseFuturesObservationService futuresObservations,
            @Value("${trading.nse.market-context.stale-ms:180000}") long staleMs,
            @Value("${trading.nse.market-context.window-tolerance-ms:90000}") long toleranceMs,
            @Value("${trading.nse.market-context.max-observations:2000}") int maxObservations
    ) {
        this.nse = nse;
        this.futuresObservations = futuresObservations;
        this.staleAfter = Duration.ofMillis(staleMs);
        this.windowTolerance = Duration.ofMillis(toleranceMs);
        this.maxObservations = Math.max(2, maxObservations);
    }

    @Scheduled(fixedDelayString = "${trading.nse.market-context.poll-interval-ms:30000}",
            initialDelayString = "${trading.nse.market-context.initial-delay-ms:1000}")
    public synchronized void collectNow() {
        collectSpot();
        collectFutures();
    }

    public synchronized NseMarketContextSnapshot snapshot() {
        Instant now = Instant.now();
        SpotObservation spot = spotHistory.isEmpty() ? null : spotHistory.lastEntry().getValue();
        NseFuturesObservation futures = futuresHistory.isEmpty() ? null : futuresHistory.lastEntry().getValue();

        Status spotStatus = sourceStatus(spot == null ? null : spot.timestamp(), spotFailure, now);
        Metric spot1m = returnMetric(spotHistory, spot == null ? null : spot.timestamp(),
                spot == null ? null : spot.price(), Duration.ofMinutes(1), spotStatus);
        Metric spot5m = returnMetric(spotHistory, spot == null ? null : spot.timestamp(),
                spot == null ? null : spot.price(), Duration.ofMinutes(5), spotStatus);
        Metric spot10m = returnMetric(spotHistory, spot == null ? null : spot.timestamp(),
            spot == null ? null : spot.price(), Duration.ofMinutes(10), spotStatus);
        Metric spot15m = returnMetric(spotHistory, spot == null ? null : spot.timestamp(),
                spot == null ? null : spot.price(), Duration.ofMinutes(15), spotStatus);
        Double spotOpeningGap = openingGapPercent(spot == null ? null : spot.price(),
                spot == null ? null : spot.previousClose());
        NiftyContext niftyContext = new NiftyContext(
            spot == null ? null : spot.sourceTimestamp(),
            spot == null ? null : spot.price(),
            spot == null ? null : spot.open(), spot == null ? null : spot.high(),
            spot == null ? null : spot.low(), spot == null ? null : spot.previousClose(),
            spot == null ? null : spot.change(), spot == null ? null : spot.changePercent(),
            spotOpeningGap, spotOpeningGap,
            spot1m, spot5m, spot10m, spot15m, spotStatus);

        Status futuresStatus = sourceStatus(futures == null ? null : futures.timestamp(), futuresFailure, now);
        Metric futures5m = returnMetric(futuresHistory, futures == null ? null : futures.timestamp(),
                futures == null ? null : futures.price(), Duration.ofMinutes(5), futuresStatus);
        Metric futures10m = returnMetric(futuresHistory, futures == null ? null : futures.timestamp(),
            futures == null ? null : futures.price(), Duration.ofMinutes(10), futuresStatus);
        Metric futures15m = returnMetric(futuresHistory, futures == null ? null : futures.timestamp(),
                futures == null ? null : futures.price(), Duration.ofMinutes(15), futuresStatus);
        Double futuresOpeningGap = openingGapPercent(futures == null ? null : futures.price(),
                futures == null ? null : futures.previousClose());
        FuturesContext futuresContext = new FuturesContext(
            futures == null ? null : futures.sourceTimestamp(),
            futures == null ? null : futures.identifier(),
            futures == null ? null : futures.instrumentType(), futures == null ? null : futures.expiry(),
            futures == null ? null : futures.price(), futures == null ? null : futures.open(),
            futures == null ? null : futures.high(), futures == null ? null : futures.low(),
            futures == null ? null : futures.previousClose(), futures == null ? null : futures.change(),
            futures == null ? null : futures.changePercent(),
            futuresOpeningGap, futuresOpeningGap,
            futures == null ? null : futures.openInterest(),
            futures == null ? null : futures.changeInOpenInterest(),
            futures == null ? null : futures.percentChangeInOpenInterest(),
            futures == null ? null : futures.volume(), futures == null ? null : futures.turnover(),
            futures == null ? null : futures.underlying(),
            futures == null ? null : futures.underlyingValue(),
                futures == null ? null : Math.round((futures.price() - futures.underlyingValue()) * 100.0) / 100.0,
                futures5m, futures10m, futures15m, futuresStatus);

        Metric lead = leadMetric(futures5m, spot5m);
        return new NseMarketContextSnapshot(now, niftyContext, futuresContext, lead);
    }

    public synchronized int spotObservationCount() {
        return spotHistory.size();
    }

    public synchronized int futuresObservationCount() {
        return futuresHistory.size();
    }

    private void collectSpot() {
        try {
            JsonNode payload = nse.indexData("All");
            JsonNode row = findNifty(payload);
            String sourceTimestamp = text(row, "timeVal", "timestamp", "lastUpdateTime");
            Instant timestamp = parseTimestamp(sourceTimestamp);
            double price = requiredNumber(row, "last", "lastPrice", "ltp");
                Double previousClose = optionalNumber(row, "previousClose", "prevClose");
                Double change = optionalNumber(row, "change");
                if (change == null && previousClose != null) change = price - previousClose;
            SpotObservation observation = new SpotObservation(timestamp, sourceTimestamp, price,
                    optionalNumber(row, "open"), optionalNumber(row, "high"), optionalNumber(row, "low"),
                    previousClose, change,
                    optionalNumber(row, "percChange", "percentChange", "changePercent"));
            putBounded(spotHistory, timestamp, observation);
            spotFailure = null;
        } catch (Exception error) {
            spotFailure = error.getMessage() == null ? "NSE NIFTY spot data unavailable" : error.getMessage();
            log.warn("[NSE-CONTEXT] Spot observation unavailable: {}", spotFailure);
        }
    }

    private void collectFutures() {
        try {
            JsonNode payload = nse.futures();
            JsonNode row = payload.path("data").path(0);
            String sourceTimestamp = text(payload, "timestamp");
            Instant timestamp = parseTimestamp(sourceTimestamp);
            String identifier = text(row, "identifier");
            double price = requiredNumber(row, "lastPrice");
            double underlyingValue = requiredNumber(row, "underlyingValue");
            Long openInterest = optionalLong(row, "openInterest");
            if (openInterest == null) throw new IllegalArgumentException("NSE Futures OI is missing.");
                NseFuturesObservation observation = new NseFuturesObservation(timestamp, sourceTimestamp, identifier,
                    text(row, "instrumentType"), text(row, "expiryDate"), price,
                    optionalNumber(row, "openPrice"), optionalNumber(row, "highPrice"), optionalNumber(row, "lowPrice"),
                    optionalNumber(row, "prevClose"), optionalNumber(row, "change"), optionalNumber(row, "pchange"),
                    openInterest, optionalLong(row, "changeinOpenInterest"),
                    optionalNumber(row, "pchangeinOpenInterest"), optionalLong(row, "totalTradedVolume"),
                    optionalNumber(row, "totalTurnover"), text(row, "underlying"), underlyingValue);
                futuresObservations.persist(observation);
            if (!futuresHistory.isEmpty()
                    && !futuresHistory.lastEntry().getValue().identifier().equals(identifier)) {
                futuresHistory.clear();
                log.info("[NSE-CONTEXT] Futures contract changed to {}; history reset", identifier);
            }
            putBounded(futuresHistory, timestamp, observation);
            futuresFailure = null;
        } catch (Exception error) {
            futuresFailure = error.getMessage() == null ? "NSE Futures data unavailable" : error.getMessage();
            log.warn("[NSE-CONTEXT] Futures observation unavailable: {}", futuresFailure);
        }
    }

    private <T> void putBounded(NavigableMap<Instant, T> history, Instant timestamp, T observation) {
        if (timestamp == null) return;
        java.time.LocalDate currentDate = timestamp.atZone(NSE_ZONE).toLocalDate();
        if (!history.isEmpty()) {
            java.time.LocalDate lastDate = history.lastEntry().getKey().atZone(NSE_ZONE).toLocalDate();
            if (!currentDate.equals(lastDate)) {
                history.clear();
            }
        }

        history.put(timestamp, observation);
        while (history.size() > maxObservations) history.pollFirstEntry();
    }

    private static Double openingGapPercent(Double currentPrice, Double previousClose) {
        if (currentPrice == null || previousClose == null || previousClose == 0.0) {
            return null;
        }
        return (currentPrice - previousClose) / previousClose * 100.0;
    }

    private <T> Metric returnMetric(NavigableMap<Instant, T> history, Instant currentTimestamp,
                                    Double currentPrice, Duration period, Status sourceStatus) {
        if (sourceStatus == Status.UNAVAILABLE || sourceStatus == Status.STALE) {
            return new Metric(null, sourceStatus);
        }
        if (currentTimestamp == null || currentPrice == null || history.isEmpty()) {
            return new Metric(null, Status.COLLECTING);
        }
        Instant target = currentTimestamp.minus(period);
        Map.Entry<Instant, T> priorEntry = history.floorEntry(target);
        if (priorEntry == null || Duration.between(priorEntry.getKey(), target).compareTo(windowTolerance) > 0) {
            return new Metric(null, Status.COLLECTING);
        }
        double priorPrice = priceOf(priorEntry.getValue());
        if (!Double.isFinite(priorPrice) || priorPrice == 0) return new Metric(null, Status.COLLECTING);
        return new Metric((currentPrice - priorPrice) / priorPrice * 100.0, Status.AVAILABLE);
    }

    private static double priceOf(Object observation) {
        if (observation instanceof SpotObservation spot) return spot.price();
        return ((NseFuturesObservation) observation).price();
    }

    private Status sourceStatus(Instant sourceTimestamp, String failure, Instant now) {
        if (failure != null) return Status.UNAVAILABLE;
        if (sourceTimestamp == null) return Status.COLLECTING;
        Duration age = Duration.between(sourceTimestamp, now);
        return age.isNegative() || age.compareTo(staleAfter) > 0 ? Status.STALE : Status.AVAILABLE;
    }

    private static Metric leadMetric(Metric futures5m, Metric nifty5m) {
        if (futures5m.status() == Status.AVAILABLE && nifty5m.status() == Status.AVAILABLE) {
            return new Metric(futures5m.value() - nifty5m.value(), Status.AVAILABLE);
        }
        if (futures5m.status() == Status.UNAVAILABLE || nifty5m.status() == Status.UNAVAILABLE) {
            return new Metric(null, Status.UNAVAILABLE);
        }
        if (futures5m.status() == Status.STALE || nifty5m.status() == Status.STALE) {
            return new Metric(null, Status.STALE);
        }
        return new Metric(null, Status.COLLECTING);
    }

    private static JsonNode findNifty(JsonNode payload) {
        JsonNode data = payload.path("data");
        if (data.isArray()) {
            for (JsonNode row : data) {
                if ("NIFTY 50".equalsIgnoreCase(row.path("indexName").asText())
                        || "NIFTY".equalsIgnoreCase(row.path("symbol").asText())) return row;
            }
        }
        throw new IllegalArgumentException("NSE index response did not contain NIFTY 50.");
    }

    private static Instant parseTimestamp(String value) {
        try {
            return LocalDateTime.parse(value, NSE_TIMESTAMP).atZone(NSE_ZONE).toInstant();
        } catch (DateTimeParseException error) {
            return Instant.parse(value);
        }
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) return value.asText();
        }
        throw new IllegalArgumentException("Required NSE field is missing: " + String.join("/", names));
    }

    private static double requiredNumber(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber() && Double.isFinite(value.asDouble())) return value.asDouble();
        }
        throw new IllegalArgumentException("Required numeric NSE field is missing: " + String.join("/", names));
    }

    private static Double optionalNumber(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber() && Double.isFinite(value.asDouble())) return value.asDouble();
        }
        return null;
    }

    private static Long optionalLong(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isIntegralNumber() ? value.asLong() : null;
    }

    private record SpotObservation(Instant timestamp, String sourceTimestamp, double price,
                                   Double open, Double high, Double low, Double previousClose,
                                   Double change, Double changePercent) {}

}