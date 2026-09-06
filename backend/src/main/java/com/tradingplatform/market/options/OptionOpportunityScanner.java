package com.tradingplatform.market.options;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.ml.MlPredictionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Evaluates CE and PE independently using Option Magnitude ML.
 *
 * The scanner does NOT force CE/PE based on NIFTY direction.
 * Both sides can be evaluated even when NIFTY is NEUTRAL.
 */
@Service
public class OptionOpportunityScanner {

    private final MlPredictionService ml;

    private final double minReturn;
    private final double maxSpread;
    private final double minVolume;
    private final double minOi;
    private final double maxAtmDistance;

    public OptionOpportunityScanner(
            MlPredictionService ml,
            @Value("${trading.options.min-predicted-return-percent:2}")
            double minReturn,
            @Value("${trading.options.max-spread-percent:10}")
            double maxSpread,
            @Value("${trading.options.min-volume:1}")
            double minVolume,
            @Value("${trading.options.min-open-interest:1}")
            double minOi,
            @Value("${trading.options.max-distance-from-atm-percent:1.5}")
            double maxAtmDistance
    ) {
        this.ml = ml;
        this.minReturn = minReturn;
        this.maxSpread = maxSpread;
        this.minVolume = minVolume;
        this.minOi = minOi;
        this.maxAtmDistance = maxAtmDistance;
    }

    public Map<String, Object> scan(
            JsonNode chain,
            String expiry,
            Map<String, Double> tech,
            JsonNode direction,
            double niftyReturn,
            boolean optionReturnModelReady
    ) {

        if (chain == null || !chain.isArray() || chain.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid or empty option chain"
            );
        }

        double spot = spot(chain);
        double atm = atm(chain, spot);

        List<Map<String, Object>> ce = new ArrayList<>();
        List<Map<String, Object>> pe = new ArrayList<>();

        List<Map<String, Object>> rejected =
                new ArrayList<>();

        Diagnostics diagnostics = new Diagnostics();

        diagnostics.totalRows = chain.size();

        for (JsonNode row : chain) {

            double strike = n(row, "strike_price");

            if (strike <= 0) {
                diagnostics.rejectedOther++;
                continue;
            }

            double distance =
                    Math.abs(strike - atm) / spot * 100;

            if (distance > maxAtmDistance) {
                diagnostics.rejectedOther++;
                continue;
            }

            diagnostics.rowsWithinAtmDistance++;

            candidate(
                    row.path("call_options"),
                    strike,
                    "CE",
                    spot,
                    expiry,
                    tech,
                    direction,
                    niftyReturn,
                    optionReturnModelReady,
                    diagnostics,
                    rejected
            ).ifPresent(ce::add);

            candidate(
                    row.path("put_options"),
                    strike,
                    "PE",
                    spot,
                    expiry,
                    tech,
                    direction,
                    niftyReturn,
                    optionReturnModelReady,
                    diagnostics,
                    rejected
            ).ifPresent(pe::add);
        }

        Comparator<Map<String, Object>> comparator =
                Comparator.comparingDouble(
                        v -> -((Number) v.get("optionScore")).doubleValue()
                );

        ce.sort(comparator);
        pe.sort(comparator);

        ce = limit(ce);
        pe = limit(pe);

        Map<String, Object> best =
                best(ce, pe);

        Map<String, Object> out =
                new LinkedHashMap<>();

        out.put("symbol", "NIFTY");
        out.put("spot", r(spot));
        out.put("atmStrike", r(atm));

        out.put(
                "recommendation",
                best == null
                        ? "NO_TRADE"
                        : best.get("type")
        );

        out.put(
                "recommendedOption",
                best
        );

        out.put("ceCandidates", ce);
        out.put("peCandidates", pe);

        out.put(
                "decisionReason",
                best == null
                        ? optionReturnModelReady
                                ? "No option met ML return, liquidity, spread, and Greek requirements."
                                : "The option-return model is not trained yet; the NIFTY direction forecast is available, but no option trade is issued."
                        : String.valueOf(best.get("reason"))
        );

        /*
         * Diagnostics
         */
        Map<String, Object> diagnosticMap =
                new LinkedHashMap<>();

        diagnosticMap.put(
                "totalRows",
                diagnostics.totalRows
        );

        diagnosticMap.put(
                "rowsWithinAtmDistance",
                diagnostics.rowsWithinAtmDistance
        );

        diagnosticMap.put(
                "ceChecked",
                diagnostics.ceChecked
        );

        diagnosticMap.put(
                "peChecked",
                diagnostics.peChecked
        );

        diagnosticMap.put(
                "ceAccepted",
                diagnostics.ceAccepted
        );

        diagnosticMap.put(
                "peAccepted",
                diagnostics.peAccepted
        );

        Map<String, Object> rejections =
                new LinkedHashMap<>();

        rejections.put(
                "missingMarketData",
                diagnostics.rejectedMissingMarketData
        );

        rejections.put(
                "invalidLtp",
                diagnostics.rejectedInvalidLtp
        );

        rejections.put(
                "liquidity",
                diagnostics.rejectedLiquidity
        );

        rejections.put(
                "invalidGreeks",
                diagnostics.rejectedInvalidGreeks
        );

        rejections.put(
                "spread",
                diagnostics.rejectedSpread
        );

        rejections.put(
                "mlPrediction",
                diagnostics.rejectedMlPrediction
        );

        rejections.put(
                "other",
                diagnostics.rejectedOther
        );

        diagnosticMap.put(
                "rejections",
                rejections
        );

        out.put(
                "diagnostics",
                diagnosticMap
        );

        /*
         * Only expose a few rejected candidates.
         * This prevents a huge API response.
         */
        if (rejected.size() > 10) {
            rejected = new ArrayList<>(
                    rejected.subList(0, 10)
            );
        }

        out.put(
                "rejectedCandidates",
                rejected
        );

        return out;
    }

    private Optional<Map<String, Object>> candidate(
            JsonNode option,
            double strike,
            String type,
            double spot,
            String expiry,
            Map<String, Double> tech,
            JsonNode dir,
            double niftyReturn,
            boolean optionReturnModelReady,
            Diagnostics diagnostics,
            List<Map<String, Object>> rejected
    ) {

        if ("CE".equals(type)) {
            diagnostics.ceChecked++;
        } else {
            diagnostics.peChecked++;
        }

        if (option == null || option.isMissingNode() || option.isNull()) {

            diagnostics.rejectedMissingMarketData++;

            addRejected(
                    rejected,
                    type,
                    strike,
                    "",
                    "MISSING_MARKET_DATA"
            );

            return Optional.empty();
        }

        JsonNode market =
                option.path("market_data");

        JsonNode greeks =
                option.path("option_greeks");

        String key =
                option.path("instrument_key")
                        .asText("");

        if (key.isBlank()) {

            diagnostics.rejectedMissingMarketData++;

            addRejected(
                    rejected,
                    type,
                    strike,
                    key,
                    "MISSING_INSTRUMENT_KEY"
            );

            return Optional.empty();
        }

        double ltp =
                n(market, "ltp");

        double bid =
                n(market, "bid_price");

        double ask =
                n(market, "ask_price");

        double volume =
                n(market, "volume");

        double oi =
                n(market, "oi");

        double iv =
                n(greeks, "iv");

        double delta =
                n(greeks, "delta");

        double gamma =
                n(greeks, "gamma");

        double theta =
                n(greeks, "theta");

        double vega =
                n(greeks, "vega");

        /*
         * Price validation
         */
        if (ltp <= 0) {

            diagnostics.rejectedInvalidLtp++;

            addRejected(
                    rejected,
                    type,
                    strike,
                    key,
                    "INVALID_LTP"
            );

            return Optional.empty();
        }

        /*
         * Liquidity validation
         */
        if (volume < minVolume || oi < minOi) {

            diagnostics.rejectedLiquidity++;

            addRejected(
                    rejected,
                    type,
                    strike,
                    key,
                    "LOW_LIQUIDITY"
            );

            return Optional.empty();
        }

        /*
         * Greeks validation
         */
        if (!finite(
                iv,
                delta,
                gamma,
                theta,
                vega
        )) {

            diagnostics.rejectedInvalidGreeks++;

            addRejected(
                    rejected,
                    type,
                    strike,
                    key,
                    "INVALID_GREEKS"
            );

            return Optional.empty();
        }

        /*
         * Spread
         */
        double spread =
                bid > 0 && ask > 0
                        ? ask - bid
                        : 0;

        double spreadPercent =
                spread > 0
                        ? spread / ltp * 100
                        : 0;

        if (spreadPercent > maxSpread) {

            diagnostics.rejectedSpread++;

            addRejected(
                    rejected,
                    type,
                    strike,
                    key,
                    "SPREAD_TOO_WIDE"
            );

            return Optional.empty();
        }

        /*
         * Build ML feature vector.
         */
        Map<String, Double> features =
                new LinkedHashMap<>(tech);

        put(
                features,

                "strike",
                strike,

                "option_type_encoded",
                "CE".equals(type) ? 1.0 : -1.0,

                "distance_from_atm_percent",
                (strike - spot) / spot * 100,

                "moneyness",
                spot / strike,

                "option_ltp",
                ltp,

                "bid",
                bid,

                "ask",
                ask,

                "spread",
                spread,

                "spread_percent",
                spreadPercent,

                "option_volume",
                volume,

                "option_oi",
                oi,

                "iv",
                iv,

                "delta",
                delta,

                "gamma",
                gamma,

                "theta",
                theta,

                "vega",
                vega,

                "time_to_expiry_minutes",
                minutes(expiry),

                "nifty_direction_down_probability",
                dir.path("probabilities")
                        .path("DOWN")
                        .asDouble(),

                "nifty_direction_neutral_probability",
                dir.path("probabilities")
                        .path("NEUTRAL")
                        .asDouble(),

                "nifty_direction_up_probability",
                dir.path("probabilities")
                        .path("UP")
                        .asDouble(),

                "nifty_predicted_return_percent",
                niftyReturn,

                "nifty_expected_move_points",
                spot * niftyReturn / 100
        );

        /*
         * Option Magnitude ML
         */
        if (!optionReturnModelReady) {
            diagnostics.rejectedMlPrediction++;
            addRejected(rejected, type, strike, key, "OPTION_RETURN_MODEL_NOT_TRAINED");
            return Optional.empty();
        }

        double predicted;

        try {

            JsonNode prediction =
            ml.predictNiftyOptionMagnitude(15,
                    features
                );

    System.out.println("========== OPTION ML RESPONSE ==========");
    System.out.println(prediction);
    System.out.println("========================================");

    predicted = prediction
            .path("predictedOptionReturnPercent")
            .asDouble(Double.NaN);

    System.out.println(
            "Option " + type +
            " " + strike +
            " predicted return = " + predicted
    );

        } catch (Exception e) {

            diagnostics.rejectedMlPrediction++;

            String rejectionReason = isModelUnavailable(e)
                    ? "OPTION_RETURN_MODEL_NOT_TRAINED"
                    : "ML_PREDICTION_FAILED";

            addRejected(
                    rejected,
                    type,
                    strike,
                    key,
                    rejectionReason
            );

            return Optional.empty();
        }

        if (!Double.isFinite(predicted)) {

            diagnostics.rejectedMlPrediction++;

            addRejected(
                    rejected,
                    type,
                    strike,
                    key,
                    "INVALID_ML_PREDICTION"
            );

            return Optional.empty();
        }

        /*
         * Minimum predicted return
         */
        if (predicted < minReturn) {

            diagnostics.rejectedMlPrediction++;

            Map<String, Object> rejectedCandidate =
                    new LinkedHashMap<>();

            rejectedCandidate.put(
                    "type",
                    type
            );

            rejectedCandidate.put(
                    "strike",
                    r(strike)
            );

            rejectedCandidate.put(
                    "instrumentKey",
                    key
            );

            rejectedCandidate.put(
                    "ltp",
                    r(ltp)
            );

            rejectedCandidate.put(
                    "predictedOptionReturnPercent",
                    r(predicted)
            );

            rejectedCandidate.put(
                    "requiredMinimumReturnPercent",
                    r(minReturn)
            );

            rejectedCandidate.put(
                    "rejectionReason",
                    "ML_RETURN_BELOW_THRESHOLD"
            );

            rejected.add(
                    rejectedCandidate
            );

            return Optional.empty();
        }

        /*
         * Quality score.
         */
        double quality =
                Math.min(
                        20,
                        Math.abs(delta) * 25
                )
                +
                Math.min(
                        8,
                        gamma * 1000
                )
                +
                Math.min(
                        12,
                        Math.log10(volume + 1) * 3
                )
                +
                Math.min(
                        10,
                        Math.log10(oi + 1) * 2
                )
                +
                (
                        spreadPercent <= 2
                                ? 10
                                : spreadPercent <= 5
                                ? 6
                                : 2
                )
                -
                Math.min(
                        8,
                        Math.abs(theta)
                );

        double score =
                predicted * 10 + quality;

        Map<String, Object> candidate =
                new LinkedHashMap<>();

        put(
                candidate,

                "type",
                type,

                "strike",
                r(strike),

                "instrumentKey",
                key,

                "expiry",
                expiry,

                "ltp",
                r(ltp),

                "bid",
                r(bid),

                "ask",
                r(ask),

                "spreadPercent",
                r(spreadPercent),

                "volume",
                volume,

                "oi",
                oi,

                "iv",
                r(iv),

                "delta",
                r(delta),

                "gamma",
                r(gamma),

                "theta",
                r(theta),

                "vega",
                r(vega),

                "predictedOptionReturnPercent",
                r(predicted),

                "optionScore",
                r(score),

                "reason",
                "Option ML predicts "
                        + r(predicted)
                        + "% over 15 minutes; passed liquidity, spread, and Greek checks."
        );

        if ("CE".equals(type)) {
            diagnostics.ceAccepted++;
        } else {
            diagnostics.peAccepted++;
        }

        return Optional.of(candidate);
    }

        private boolean isModelUnavailable(Exception exception) {
                Throwable current = exception;
                while (current != null) {
                        if (current.getMessage() != null
                                        && current.getMessage().contains("OPTION_MAGNITUDE_MODEL_NOT_TRAINED")) {
                                return true;
                        }
                        current = current.getCause();
                }
                return false;
        }

    private static void addRejected(
            List<Map<String, Object>> rejected,
            String type,
            double strike,
            String key,
            String reason
    ) {

        Map<String, Object> item =
                new LinkedHashMap<>();

        item.put("type", type);
        item.put("strike", r(strike));
        item.put("instrumentKey", key);
        item.put("rejectionReason", reason);

        rejected.add(item);
    }

   private static <K, V> void put(
        Map<K, V> map,
        Object... values
) {
    for (int i = 0; i < values.length; i += 2) {
        @SuppressWarnings("unchecked")
        K key = (K) values[i];

        @SuppressWarnings("unchecked")
        V value = (V) values[i + 1];

        map.put(key, value);
    }
}

    private static Map<String, Object> best(
            List<Map<String, Object>> ce,
            List<Map<String, Object>> pe
    ) {

        Map<String, Object> x =
                ce.isEmpty() ? null : ce.get(0);

        Map<String, Object> y =
                pe.isEmpty() ? null : pe.get(0);

        if (x == null) return y;
        if (y == null) return x;

        double xScore =
                ((Number) x.get("optionScore"))
                        .doubleValue();

        double yScore =
                ((Number) y.get("optionScore"))
                        .doubleValue();

        return xScore >= yScore ? x : y;
    }

    private static List<Map<String, Object>> limit(
            List<Map<String, Object>> values
    ) {

        return values.size() <= 5
                ? values
                : new ArrayList<>(
                        values.subList(0, 5)
                );
    }

    private static boolean finite(
            double... values
    ) {

        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }

        return true;
    }

    private static double n(
            JsonNode node,
            String key
    ) {

        return node.path(key).asDouble(0);
    }

    private static double spot(
            JsonNode chain
    ) {

        for (JsonNode row : chain) {

            double value =
                    n(row, "underlying_spot_price");

            if (value > 0) {
                return value;
            }
        }

        throw new IllegalArgumentException(
                "Underlying NIFTY spot price not found"
        );
    }

    private static double atm(
            JsonNode chain,
            double spot
    ) {

        return java.util.stream.StreamSupport
                .stream(
                        chain.spliterator(),
                        false
                )
                .mapToDouble(
                        row -> n(
                                row,
                                "strike_price"
                        )
                )
                .filter(v -> v > 0)
                .boxed()
                .min(
                        Comparator.comparingDouble(
                                v -> Math.abs(v - spot)
                        )
                )
                .orElseThrow();
    }

    /**
     * Expiry is interpreted in India time.
     *
     * We use the beginning of the following calendar day
     * as a conservative expiry boundary.
     */
    private static double minutes(
            String expiry
    ) {

        try {

            ZoneId india =
                    ZoneId.of("Asia/Kolkata");

            LocalDate expiryDate =
                    LocalDate.parse(expiry);

            ZonedDateTime expiryBoundary =
                    expiryDate
                            .plusDays(1)
                            .atStartOfDay(india);

            return Math.max(
                    0,
                    Duration.between(
                            ZonedDateTime.now(india),
                            expiryBoundary
                    ).toMinutes()
            );

        } catch (Exception e) {

            return 0;
        }
    }

    private static double r(
            double value
    ) {

        return BigDecimal
                .valueOf(value)
                .setScale(
                        4,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private static class Diagnostics {

        int totalRows;

        int rowsWithinAtmDistance;

        int ceChecked;
        int peChecked;

        int ceAccepted;
        int peAccepted;

        int rejectedMissingMarketData;
        int rejectedInvalidLtp;
        int rejectedLiquidity;
        int rejectedInvalidGreeks;
        int rejectedSpread;
        int rejectedMlPrediction;
        int rejectedOther;
    }
}
