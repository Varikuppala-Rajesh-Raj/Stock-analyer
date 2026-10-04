package com.tradingplatform.market.options;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.persistence.OptionMarketSnapshotEntity;
import com.tradingplatform.persistence.OptionMarketSnapshotRepository;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OptionSnapshotService {
    public record OptionPricePoint(Instant timestamp, BigDecimal price, BigDecimal volume) {}

    private final OptionMarketSnapshotRepository repository;
    private final ObjectMapper objectMapper;
    public OptionSnapshotService(OptionMarketSnapshotRepository repository, ObjectMapper objectMapper) { this.repository = repository; this.objectMapper = objectMapper; }

    /** Persists a single five-minute contract observation; the database unique key makes retries idempotent. */
    @Transactional
    public int persist(String expiryText, JsonNode chain, Map<String, Double> technicalFeatures) {
        LocalDate expiry = LocalDate.parse(expiryText);
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MINUTES).minusSeconds(Instant.now().getEpochSecond() % 300 % 60); // aligned below
        timestamp = Instant.ofEpochSecond((Instant.now().getEpochSecond() / 300) * 300);
        int stored = 0;
        for (JsonNode row : chain) {
            double spot = number(row, "underlying_spot_price"); double strike = number(row, "strike_price");
            stored += save(timestamp, expiry, spot, strike, "CE", row.path("call_options"), technicalFeatures) ? 1 : 0;
            stored += save(timestamp, expiry, spot, strike, "PE", row.path("put_options"), technicalFeatures) ? 1 : 0;
        }
        return stored;
    }

    @Transactional(readOnly = true)
    public List<OptionPricePoint> history(String instrumentKey, String expiryText, LocalDate from, LocalDate to) {
        if (instrumentKey == null || instrumentKey.isBlank()) {
            throw new IllegalArgumentException("Option instrument key is required.");
        }
        LocalDate expiry = LocalDate.parse(expiryText);
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("History start date must be on or before the end date.");
        }
        Instant start = from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        return repository.findByInstrumentKeyAndTimestampBetweenOrderByTimestampAsc(instrumentKey, start, end)
                .stream()
                .filter(snapshot -> expiry.equals(snapshot.expiry))
                .map(snapshot -> new OptionPricePoint(snapshot.timestamp, snapshot.ltp,
                        snapshot.volume == null ? BigDecimal.ZERO : snapshot.volume))
                .toList();
    }
    private boolean save(Instant timestamp, LocalDate expiry, double spot, double strike, String type, JsonNode option, Map<String, Double> technical) {
        String key = option.path("instrument_key").asText(""); JsonNode market = option.path("market_data");
        double ltp = number(market, "ltp");
        if (key.isBlank() || ltp <= 0 || spot <= 0 || strike <= 0 || repository.findByTimestampAndInstrumentKey(timestamp, key).isPresent()) return false;
        OptionMarketSnapshotEntity e = new OptionMarketSnapshotEntity(); e.timestamp=timestamp; e.symbol="NIFTY"; e.expiry=expiry; e.strikePrice=bd(strike); e.optionType=type; e.instrumentKey=key; e.underlyingSpot=bd(spot); e.ltp=bd(ltp);
        e.bid=nullable(number(market,"bid_price")); e.ask=nullable(number(market,"ask_price")); e.volume=nullable(number(market,"volume")); e.oi=nullable(number(market,"oi")); JsonNode g=option.path("option_greeks"); e.iv=nullable(number(g,"iv")); e.delta=nullable(number(g,"delta")); e.gamma=nullable(number(g,"gamma")); e.theta=nullable(number(g,"theta")); e.vega=nullable(number(g,"vega"));
        try { e.technicalFeatures=objectMapper.writeValueAsString(technical); } catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to serialize option snapshot features", ex); }
        repository.save(e); return true;
    }
    private static double number(JsonNode n,String f){ return n.path(f).asDouble(0); }
    private static BigDecimal bd(double value){ return BigDecimal.valueOf(value); }
    private static BigDecimal nullable(double value){ return value == 0 ? null : bd(value); }
}
