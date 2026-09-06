package com.tradingplatform.market.options;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.ml.HistoricalNiftyPredictionService;
import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.persistence.OptionMarketSnapshotEntity;
import com.tradingplatform.persistence.OptionMarketSnapshotRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OptionTrainingDatasetService {

    private final OptionMarketSnapshotRepository snapshots;
    private final ObjectMapper json;
    private final HistoricalNiftyPredictionService historicalNifty;

    public OptionTrainingDatasetService(
            OptionMarketSnapshotRepository snapshots,
            ObjectMapper json,
            HistoricalNiftyPredictionService historicalNifty
    ) {
        this.snapshots = snapshots;
        this.json = json;
        this.historicalNifty = historicalNifty;
    }

    /**
     * Generates option training rows.
     *
     * Features come only from information available at the
     * current snapshot timestamp.
     *
     * Target comes from the same option contract at
     * current timestamp + horizon.
     */
    public List<MlPredictionService.OptionMagnitudeTrainingRow> generate(
            int horizonMinutes
    ) {

        List<MlPredictionService.OptionMagnitudeTrainingRow> rows =
                new ArrayList<>();

        for (OptionMarketSnapshotEntity current :
                snapshots.findBySymbolOrderByTimestampAsc("NIFTY")) {

            if (current.ltp == null
                    || current.ltp.signum() <= 0) {
                continue;
            }

            /*
             * Find the same option contract approximately
             * horizonMinutes into the future.
             */
            var from =
                    current.timestamp.plus(
                            Duration.ofMinutes(horizonMinutes - 2)
                    );

            var to =
                    current.timestamp.plus(
                            Duration.ofMinutes(horizonMinutes + 2)
                    );

            var future =
                    snapshots
                            .findByInstrumentKeyAndTimestampBetweenOrderByTimestampAsc(
                                    current.instrumentKey,
                                    from,
                                    to
                            )
                            .stream()
                            .findFirst();

            if (future.isEmpty()
                    || future.get().ltp == null
                    || future.get().ltp.signum() <= 0) {
                continue;
            }

            try {

                Map<String, Double> technicalFeatures =
                        json.readValue(
                                current.technicalFeatures,
                                new TypeReference<Map<String, Double>>() {}
                        );

                double spot =
                        value(current.underlyingSpot);

                double strike =
                        value(current.strikePrice);

                if (spot <= 0 || strike <= 0) {
                    continue;
                }

                /*
                 * Historical NIFTY prediction.
                 *
                 * This uses only NIFTY data available at
                 * current.timestamp.
                 */
                HistoricalNiftyPredictionService.NiftyContext nifty =
                        historicalNifty.predictAt(
                                current.timestamp,
                                horizonMinutes
                        );

                Map<String, Double> f =
                        new LinkedHashMap<>(technicalFeatures);

                double bid =
                        value(current.bid);

                double ask =
                        value(current.ask);

                double spread =
                        bid > 0 && ask > 0
                                ? ask - bid
                                : 0;

                double ltp =
                        value(current.ltp);

                /*
                 * Option-specific features.
                 */
                f.put(
                        "strike",
                        strike
                );

                f.put(
                        "option_type_encoded",
                        "CE".equals(current.optionType)
                                ? 1d
                                : -1d
                );

                f.put(
                        "distance_from_atm_percent",
                        (strike - spot) / spot * 100d
                );

                f.put(
                        "moneyness",
                        spot / strike
                );

                f.put(
                        "option_ltp",
                        ltp
                );

                f.put(
                        "bid",
                        bid
                );

                f.put(
                        "ask",
                        ask
                );

                f.put(
                        "spread",
                        spread
                );

                f.put(
                        "spread_percent",
                        spread > 0 && ltp > 0
                                ? spread / ltp * 100d
                                : 0d
                );

                f.put(
                        "option_volume",
                        value(current.volume)
                );

                f.put(
                        "option_oi",
                        value(current.oi)
                );

                f.put(
                        "iv",
                        value(current.iv)
                );

                f.put(
                        "delta",
                        value(current.delta)
                );

                f.put(
                        "gamma",
                        value(current.gamma)
                );

                f.put(
                        "theta",
                        value(current.theta)
                );

                f.put(
                        "vega",
                        value(current.vega)
                );

                f.put(
                        "time_to_expiry_minutes",
                        Duration.between(
                                current.timestamp,
                                current.expiry
                                        .plusDays(1)
                                        .atStartOfDay(
                                                java.time.ZoneOffset.UTC
                                        )
                                        .toInstant()
                        ).toMinutes() * 1d
                );

                /*
                 * REAL NIFTY MODEL FEATURES.
                 *
                 * No more zeros.
                 */
                f.put(
                        "nifty_direction_down_probability",
                        nifty.downProbability()
                );

                f.put(
                        "nifty_direction_neutral_probability",
                        nifty.neutralProbability()
                );

                f.put(
                        "nifty_direction_up_probability",
                        nifty.upProbability()
                );

                f.put(
                        "nifty_predicted_return_percent",
                        nifty.predictedReturnPercent()
                );

                f.put(
                        "nifty_expected_move_points",
                        nifty.expectedMovePoints()
                );

                /*
                 * Reject incomplete / invalid rows.
                 */
                boolean invalid =
                        f.values()
                                .stream()
                                .anyMatch(
                                        value ->
                                                value == null
                                                        || !Double.isFinite(
                                                        value
                                                )
                                );

                if (invalid) {
                    continue;
                }

                /*
                 * SUPERVISED TARGET.
                 *
                 * Future data is used ONLY here.
                 */
                double futureLtp =
                        value(future.get().ltp);

                double target =
                        (futureLtp - ltp)
                                / ltp
                                * 100d;

                if (!Double.isFinite(target)) {
                    continue;
                }

                rows.add(
                        new MlPredictionService.OptionMagnitudeTrainingRow(
                                current.timestamp.toString(),
                                f,
                                target
                        )
                );

            } catch (Exception e) {
    System.err.println(
            "OPTION TRAINING ROW FAILED: timestamp="
                    + current.timestamp
                    + ", instrument="
                    + current.instrumentKey
                    + ", error="
                    + e.getMessage()
    );

    e.printStackTrace();
}
        }

        return rows;
    }

    private static double value(
            java.math.BigDecimal value
    ) {
        return value == null
                ? 0d
                : value.doubleValue();
    }
}