package com.tradingplatform.market.options;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OptionContractSelector {
    public record Contract(
            String instrumentKey,
            String optionType,
            BigDecimal strike,
            BigDecimal spot,
            BigDecimal ltp,
            BigDecimal bid,
            BigDecimal ask,
            BigDecimal volume,
            BigDecimal openInterest,
            BigDecimal changeInOpenInterest,
            BigDecimal delta,
            BigDecimal spreadPercent,
            double score
    ) {}

    private final BigDecimal maxSpreadPercent;
    private final BigDecimal minVolume;
    private final BigDecimal minOpenInterest;
    private final BigDecimal maxAtmDistancePercent;
    private final BigDecimal minAbsDelta;
    private final BigDecimal maxAbsDelta;

    public OptionContractSelector(
            @Value("${trading.paper.automation.max-spread-percent:3}") BigDecimal maxSpreadPercent,
            @Value("${trading.paper.automation.min-volume:100}") BigDecimal minVolume,
            @Value("${trading.paper.automation.min-open-interest:1000}") BigDecimal minOpenInterest,
            @Value("${trading.paper.automation.max-atm-distance-percent:1}") BigDecimal maxAtmDistancePercent,
            @Value("${trading.paper.automation.min-abs-delta:0.25}") BigDecimal minAbsDelta,
            @Value("${trading.paper.automation.max-abs-delta:0.75}") BigDecimal maxAbsDelta
    ) {
        this.maxSpreadPercent = maxSpreadPercent;
        this.minVolume = minVolume;
        this.minOpenInterest = minOpenInterest;
        this.maxAtmDistancePercent = maxAtmDistancePercent;
        this.minAbsDelta = minAbsDelta;
        this.maxAbsDelta = maxAbsDelta;
    }

    public Optional<Contract> select(JsonNode chainResponse, String optionType) {
        if (!"CE".equals(optionType) && !"PE".equals(optionType)) {
            throw new IllegalArgumentException("Option type must be CE or PE.");
        }
        JsonNode rows = chainResponse != null && chainResponse.path("data").isArray()
                ? chainResponse.path("data")
                : chainResponse;
        if (rows == null || !rows.isArray()) return Optional.empty();

        return java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .map(row -> candidate(row, optionType))
                .flatMap(Optional::stream)
                .max(Comparator.comparingDouble(Contract::score));
    }

    private Optional<Contract> candidate(JsonNode row, String optionType) {
        BigDecimal strike = decimal(row, "strike_price");
        BigDecimal spot = decimal(row, "underlying_spot_price");
        JsonNode option = row.path("CE".equals(optionType) ? "call_options" : "put_options");
        JsonNode market = option.path("market_data");
        JsonNode greeks = option.path("option_greeks");
        String key = option.path("instrument_key").asText("");
        BigDecimal ltp = decimal(market, "ltp");
        BigDecimal bid = decimal(market, "bid_price");
        BigDecimal ask = decimal(market, "ask_price");
        BigDecimal volume = decimal(market, "volume");
        BigDecimal oi = decimal(market, "oi");
        BigDecimal changeOi = decimal(market, "change_oi");
        if (changeOi == null) changeOi = decimal(market, "change_in_oi");
        BigDecimal delta = decimal(greeks, "delta");
        if (key.isBlank() || strike == null || spot == null || spot.signum() <= 0
                || ltp == null || bid == null || ask == null || volume == null || oi == null
                || delta == null || bid.signum() <= 0 || ask.compareTo(bid) < 0
                || ltp.signum() <= 0 || volume.compareTo(minVolume) < 0
                || oi.compareTo(minOpenInterest) < 0) return Optional.empty();

        BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2), 8, java.math.RoundingMode.HALF_UP);
        BigDecimal spread = ask.subtract(bid).multiply(BigDecimal.valueOf(100))
                .divide(mid, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal distance = strike.subtract(spot).abs().multiply(BigDecimal.valueOf(100))
                .divide(spot, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal absDelta = delta.abs();
        if (spread.compareTo(maxSpreadPercent) > 0
                || distance.compareTo(maxAtmDistancePercent) > 0
                || absDelta.compareTo(minAbsDelta) < 0
                || absDelta.compareTo(maxAbsDelta) > 0) return Optional.empty();

        double score = 50.0 - distance.doubleValue() * 10.0 - spread.doubleValue() * 3.0
                - Math.abs(absDelta.doubleValue() - 0.5) * 20.0
                + Math.log1p(volume.doubleValue()) + Math.log1p(oi.doubleValue()) / 4.0
                + (changeOi == null ? 0 : Math.signum(changeOi.doubleValue()) * 0.25);
        return Optional.of(new Contract(key, optionType, strike, spot, ltp, bid, ask, volume, oi,
                changeOi, delta, spread, score));
    }

    private static BigDecimal decimal(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (value.isNumber()) return value.decimalValue();
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
