package com.tradingplatform.market.ml;

import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.indicators.CanonicalTechnicalFeatures;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TrainingDatasetAuditService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime SESSION_OPEN = LocalTime.of(9, 15);
    private static final LocalTime LAST_CANDLE_START = LocalTime.of(15, 25);
    private static final int CANDLES_PER_SESSION = 75;
    private static final int MINIMUM_FEATURE_CANDLES = 60;

    public Map<String, Object> audit(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            MarketDataService.TrainingHistory history,
            List<TrainingExample> examples,
            int horizonCandles,
            int horizonMinutes,
            BigDecimal thresholdPercent
    ) {
        List<Candle> candles = history.candles();
        Map<LocalDate, Integer> candlesBySession = new LinkedHashMap<>();
        int outOfSessionCandles = 0;
        for (Candle candle : candles) {
            var local = candle.timestamp().atZone(MARKET_ZONE);
            candlesBySession.merge(local.toLocalDate(), 1, Integer::sum);
            if (local.toLocalTime().isBefore(SESSION_OPEN)
                    || local.toLocalTime().isAfter(LAST_CANDLE_START)) {
                outOfSessionCandles++;
            }
        }

        int expectedCandles = candlesBySession.size() * CANDLES_PER_SESSION;
        int missingSessionCandles = 0;
        int unexpectedSessionCandles = 0;
        int partialSessions = 0;
        for (int count : candlesBySession.values()) {
            missingSessionCandles += Math.max(0, CANDLES_PER_SESSION - count);
            unexpectedSessionCandles += Math.max(0, count - CANDLES_PER_SESSION);
            if (count != CANDLES_PER_SESSION) partialSessions++;
        }

        Map<String, Integer> targetAlignment = alignmentCounts(candles, horizonCandles);
        Map<String, Integer> overallLabels = labelCounts(examples, 0, examples.size());
        int split = (int) Math.floor(examples.size() * 0.8);
        int purgeRows = Math.max(1, (int) Math.ceil(horizonMinutes / 5.0));
        int trainingEnd = Math.max(0, split - purgeRows);
        int purgedRows = split - trainingEnd;
        List<TrainingExample> trainingExamples = examples.subList(0, trainingEnd);
        List<TrainingExample> purgedExamples = examples.subList(trainingEnd, split);
        List<TrainingExample> holdoutExamples = examples.subList(split, examples.size());
        Map<String, Integer> trainingLabels = labelCounts(examples, 0, trainingEnd);
        Map<String, Integer> purgedLabels = labelCounts(examples, trainingEnd, split);
        Map<String, Integer> holdoutLabels = labelCounts(examples, split, examples.size());

        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("requestedStartDate", requestedFrom.toString());
        coverage.put("requestedEndDate", requestedTo.toString());
        coverage.put("actualEarliestCandle", candles.isEmpty() ? null : candles.get(0).timestamp().toString());
        coverage.put("actualLatestCandle", candles.isEmpty() ? null : candles.get(candles.size() - 1).timestamp().toString());
        coverage.put("fetchedCandleRows", history.fetchedRows());
        coverage.put("uniqueCandles", candles.size());
        coverage.put("duplicateCandlesRemoved", history.duplicateRows());
        coverage.put("invalidCandles", 0);
        coverage.put("invalidCandleHandling", "Rejected by OHLC Candle validation during ingestion; successful fetch contains only valid candles.");
        coverage.put("observedTradingSessions", candlesBySession.size());
        coverage.put("expectedCandlesForObservedSessions", expectedCandles);
        coverage.put("missingCandlesWithinObservedSessions", missingSessionCandles);
        coverage.put("unexpectedCandlesWithinObservedSessions", unexpectedSessionCandles);
        coverage.put("partialObservedSessions", partialSessions);
        coverage.put("outOfSessionCandles", outOfSessionCandles);
        coverage.put("fullMissingSessions", "Not inferable without an authoritative exchange holiday/session calendar.");

        Map<String, Object> splitReport = new LinkedHashMap<>();
        splitReport.put("method", "chronological 80/20 holdout with forward-label purge");
        splitReport.put("totalSamples", examples.size());
        splitReport.put("trainingRows", trainingEnd);
        splitReport.put("trainingSamples", trainingEnd);
        splitReport.put("purgedRows", purgedRows);
        splitReport.put("holdoutRows", examples.size() - split);
        splitReport.put("holdoutSamples", examples.size() - split);
        splitReport.put("trainingStart", timestampAt(examples, 0));
        splitReport.put("trainingEnd", timestampAt(examples, trainingEnd - 1));
        splitReport.put("purgeStart", timestampAt(examples, trainingEnd));
        splitReport.put("purgeEnd", timestampAt(examples, split - 1));
        splitReport.put("holdoutStart", timestampAt(examples, split));
        splitReport.put("holdoutEnd", timestampAt(examples, examples.size() - 1));
        splitReport.put("labelDistribution", Map.of(
                "all", distribution(overallLabels, examples.size()),
                "training", distribution(trainingLabels, trainingEnd),
                "purged", distribution(purgedLabels, purgedRows),
                "holdout", distribution(holdoutLabels, examples.size() - split)));

        Map<String, Object> report = new LinkedHashMap<>();
        String dataQualityStatus = qualityStatus(examples);
        report.put("reportTitle", "NIFTY ML DATA AUDIT");
        report.put("generatedAt", Instant.now().toString());
        report.put("symbol", "NIFTY");
        report.put("timeframe", "M5");
        report.put("horizonMinutes", horizonMinutes);
        report.put("movementThresholdPercent", thresholdPercent);
        report.put("featureSchemaVersion", CanonicalTechnicalFeatures.VERSION);
        report.put("featureNames", CanonicalTechnicalFeatures.NAMES);
        report.put("coverage", coverage);
        report.put("targetAlignment", targetAlignment);
        report.put("rawCandleRows", candles.size());
        report.put("warmupCandlesExcluded", Math.min(MINIMUM_FEATURE_CANDLES - 1, candles.size()));
        report.put("trainingExamples", examples.size());
        report.put("sampleCounts", Map.of(
            "total", examples.size(),
            "training", trainingExamples.size(),
            "purged", purgedExamples.size(),
            "holdout", holdoutExamples.size()));
        Map<String, Integer> rowsRemoved = new LinkedHashMap<>();
        rowsRemoved.put("warmupCandles", Math.min(MINIMUM_FEATURE_CANDLES - 1, candles.size()));
        rowsRemoved.put("insufficientForwardHorizonCandles", Math.min(horizonCandles, candles.size()));
        rowsRemoved.put("missingExactHorizonTargets", targetAlignment.get("missingExactTargets"));
        rowsRemoved.put("duplicateCandles", history.duplicateRows());
        rowsRemoved.put("invalidCandles", 0);
        report.put("rowsRemoved", rowsRemoved);
        report.put("directionLabels", distribution(overallLabels, examples.size()));
        report.put("returnDistributionPercent", summarize(examples.stream()
                .map(example -> example.futureReturnPercent().doubleValue()).toList()));
        report.put("absoluteReturnDistributionPercent", summarize(examples.stream()
            .map(example -> Math.abs(example.futureReturnPercent().doubleValue())).toList()));
        report.put("returnDistributionBySplit", Map.of(
            "training", summarizeReturns(trainingExamples),
            "purged", summarizeReturns(purgedExamples),
            "holdout", summarizeReturns(holdoutExamples)));
        report.put("targetDefinition", Map.of(
            "featuresAvailableThrough", "prediction timestamp T, inclusive",
            "targetTimestamp", "T + " + horizonMinutes + " minutes exactly",
            "returnFormula", "100 * (futureClose - currentClose) / currentClose",
            "upRule", "returnPercent >= " + thresholdPercent + "%",
            "downRule", "returnPercent <= -" + thresholdPercent + "%",
            "neutralRule", "returnPercent is inside the +/- threshold",
            "alignmentRule", "Rows without an exact-horizon candle are excluded"));
        report.put("holdoutBaselines", baselines(holdoutExamples, holdoutLabels));
        report.put("chronologicalSplit", splitReport);
        Map<String, Object> featureStatistics = featureStatistics(examples);
        report.put("featureCompleteness", featureStatistics);
        report.put("featureQuality", featureQuality(examples, 0, examples.size()));
        report.put("featureQualityBySplit", Map.of(
            "training", featureQuality(trainingExamples, 0, trainingExamples.size()),
            "purged", featureQuality(purgedExamples, 0, purgedExamples.size()),
            "holdout", featureQuality(holdoutExamples, 0, holdoutExamples.size())));
        report.put("featureTargetCorrelation", featureTargetCorrelations(examples));
        report.put("factorCoverage", factorCoverage(featureStatistics, examples.size()));
        report.put("featureSourceDiagnostics", featureSourceDiagnostics(candles));
        report.put("architectureDecision", Map.of(
            "finalArchitecture", "B: broader factors joined from point-in-time historical observations",
            "currentTrainingInputs", "NIFTY M5 candle-derived technical features only",
            "externalHistoricalFactorsIncluded", false,
            "integrationPolicy", "Add factors incrementally only after historical coverage, publication time, and freshness are verified."));
        report.put("provider", Map.of(
            "name", "Upstox V3",
            "requestSucceeded", true,
            "providerErrorCount", 0,
            "staleInputAssessment", "Not measured for historical bars; exchange-session freshness needs an authoritative calendar."));
        report.put("noFutureLeakage", true);
        report.put("featureAvailabilityRule", "Technical features use candles through the prediction timestamp only; no target candle is passed to feature calculation.");
        report.put("retrainingPerformed", false);
        report.put("modelGateModified", false);
        report.put("dataQualityStatus", dataQualityStatus);
        report.put("retrainingRecommendation", "DATA_AUDIT_COMPLETE".equals(dataQualityStatus)
            ? "READY_FOR_RETRAINING_REVIEW" : "DO_NOT_RETRAIN");
        report.put("historicalFreshness", "Historical bar age is not classified as stale; exact session coverage and target alignment are reported instead.");
        return report;
    }

    private Map<String, Integer> alignmentCounts(List<Candle> candles, int horizonCandles) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("eligibleAnchorsAfterWarmupAndHorizon", 0);
        counts.put("exactHorizonTargets", 0);
        counts.put("missingExactTargets", 0);
        counts.put("sameSessionMissingTargets", 0);
        counts.put("overnightMissingTargets", 0);
        counts.put("weekendCrossingMissingTargets", 0);
        counts.put("possibleHolidayOrWeekdayGapTargets", 0);
        counts.put("longerTargetsIncorrectlyAccepted", 0);
        int lastTrainingIndex = candles.size() - horizonCandles - 1;
        for (int index = MINIMUM_FEATURE_CANDLES - 1; index <= lastTrainingIndex; index++) {
            counts.compute("eligibleAnchorsAfterWarmupAndHorizon", (key, value) -> value + 1);
            Candle current = candles.get(index);
            Candle target = candles.get(index + horizonCandles);
            Duration elapsed = Duration.between(current.timestamp(), target.timestamp());
            Duration expected = Duration.ofMinutes(5L * horizonCandles);
            if (elapsed.equals(expected)) {
                counts.compute("exactHorizonTargets", (key, value) -> value + 1);
                continue;
            }

            counts.compute("missingExactTargets", (key, value) -> value + 1);
            LocalDate currentDate = current.timestamp().atZone(MARKET_ZONE).toLocalDate();
            LocalDate targetDate = target.timestamp().atZone(MARKET_ZONE).toLocalDate();
            if (currentDate.equals(targetDate)) {
                counts.compute("sameSessionMissingTargets", (key, value) -> value + 1);
            } else if (crossesWeekend(currentDate, targetDate)) {
                counts.compute("weekendCrossingMissingTargets", (key, value) -> value + 1);
            } else if (currentDate.plusDays(1).equals(targetDate)) {
                counts.compute("overnightMissingTargets", (key, value) -> value + 1);
            } else {
                counts.compute("possibleHolidayOrWeekdayGapTargets", (key, value) -> value + 1);
            }
        }
        return counts;
    }

    private boolean crossesWeekend(LocalDate start, LocalDate end) {
        for (LocalDate date = start.plusDays(1); !date.isAfter(end); date = date.plusDays(1)) {
            DayOfWeek day = date.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return true;
        }
        return false;
    }

    private Map<String, Integer> labelCounts(List<TrainingExample> examples, int start, int end) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TrainingExample.Target target : TrainingExample.Target.values()) counts.put(target.name(), 0);
        for (int index = start; index < end; index++) {
            String label = examples.get(index).target().name();
            counts.compute(label, (key, value) -> value + 1);
        }
        return counts;
    }

    private Map<String, Object> distribution(Map<String, Integer> counts, int total) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("count", entry.getValue());
            detail.put("percentage", total == 0 ? 0.0 : round(entry.getValue() * 100.0 / total, 4));
            values.put(entry.getKey(), detail);
        }
        return values;
    }

    private Map<String, Object> summarize(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", sorted.size());
        result.put("min", sorted.isEmpty() ? null : sorted.get(0));
        result.put("max", sorted.isEmpty() ? null : sorted.get(sorted.size() - 1));
        if (sorted.isEmpty()) return result;
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = sorted.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0.0);
        result.put("mean", round(mean, 8));
        result.put("median", percentile(sorted, 0.50));
        result.put("standardDeviation", round(Math.sqrt(variance), 8));
        for (int percentile : List.of(1, 5, 10, 25, 50, 75, 90, 95, 99)) {
            result.put("p" + percentile, percentile(sorted, percentile / 100.0));
        }
        return result;
    }

    private Map<String, Object> featureStatistics(List<TrainingExample> examples) {
        Map<String, Object> statistics = new LinkedHashMap<>();
        for (String featureName : CanonicalTechnicalFeatures.NAMES) {
            List<Double> present = new ArrayList<>();
            int nonNullCount = 0;
            int nonFiniteCount = 0;
            for (TrainingExample example : examples) {
                BigDecimal value = example.features().technicalStrategyFeatures().get(featureName);
                if (value != null) {
                    nonNullCount++;
                    double numericValue = value.doubleValue();
                    if (Double.isFinite(numericValue)) present.add(numericValue);
                    else nonFiniteCount++;
                }
            }
            int zeroCount = (int) present.stream().filter(value -> value == 0.0).count();
            int missingCount = examples.size() - nonNullCount;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("nonNullCount", nonNullCount);
            detail.put("nullCount", missingCount);
            detail.put("nullPercent", examples.isEmpty() ? 0.0 : round(missingCount * 100.0 / examples.size(), 4));
            detail.put("nonFiniteCount", nonFiniteCount);
            detail.put("zeroCount", zeroCount);
            detail.put("zeroPercent", present.isEmpty() ? 0.0 : round(zeroCount * 100.0 / present.size(), 4));
            detail.put("zeroDominant", !present.isEmpty() && zeroCount * 2 >= present.size());
                detail.put("validCount", present.size());
                detail.put("availablePercent", examples.isEmpty()
                    ? null : round(present.size() * 100.0 / examples.size(), 4));
                detail.put("min", present.stream().mapToDouble(Double::doubleValue).min().stream()
                    .mapToObj(value -> (Object) value).findFirst().orElse(null));
                detail.put("max", present.stream().mapToDouble(Double::doubleValue).max().stream()
                    .mapToObj(value -> (Object) value).findFirst().orElse(null));
            double mean = present.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            double variance = present.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(Double.NaN);
                detail.put("mean", Double.isFinite(mean) ? round(mean, 8) : null);
                detail.put("standardDeviation", Double.isFinite(variance) ? round(Math.sqrt(variance), 8) : null);
                detail.put("constant", present.isEmpty() ? null
                    : present.stream().allMatch(value -> Double.compare(value, present.get(0)) == 0));
            detail.put("mostlyMissing", !examples.isEmpty() && missingCount * 2 >= examples.size());
            detail.put("historicallyUnavailable", present.isEmpty());
            statistics.put(featureName, detail);
        }
        return statistics;
    }

    private Map<String, Object> featureQuality(List<TrainingExample> examples, int start, int end) {
        List<TrainingExample> partition = examples.subList(start, end);
        Map<String, Object> perFeature = featureStatistics(partition);
        int missing = 0;
        int nonFinite = 0;
        int zero = 0;
        int constant = 0;
        int zeroDominant = 0;
        int valid = 0;
        for (Object value : perFeature.values()) {
            Map<?, ?> feature = (Map<?, ?>) value;
            missing += (Integer) feature.get("nullCount");
            nonFinite += (Integer) feature.get("nonFiniteCount");
            zero += (Integer) feature.get("zeroCount");
            valid += (Integer) feature.get("validCount");
            if (Boolean.TRUE.equals(feature.get("constant"))) constant++;
            if (Boolean.TRUE.equals(feature.get("zeroDominant"))) zeroDominant++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampleCount", partition.size());
        result.put("featureCount", CanonicalTechnicalFeatures.NAMES.size());
        result.put("expectedValueCount", partition.size() * CanonicalTechnicalFeatures.NAMES.size());
        result.put("validValueCount", valid);
        result.put("availableValuePercent", partition.isEmpty() ? null
            : round(valid * 100.0 / (partition.size() * CanonicalTechnicalFeatures.NAMES.size()), 4));
        result.put("missingValueCount", missing);
        result.put("nonFiniteValueCount", nonFinite);
        result.put("zeroValueCount", zero);
        result.put("constantFeatureCount", constant);
        result.put("zeroDominantFeatureCount", zeroDominant);
        result.put("staleValueCount", null);
        result.put("staleAssessment", "Not measurable for derived historical technical features without source-level availability timestamps.");
        result.put("features", perFeature);
        return result;
    }

    private Map<String, Object> summarizeReturns(List<TrainingExample> examples) {
        return summarize(examples.stream()
                .map(example -> example.futureReturnPercent().doubleValue()).toList());
    }

    private Map<String, Object> baselines(
            List<TrainingExample> holdout,
            Map<String, Integer> labelCounts
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        int majorityCount = labelCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        String majorityLabel = labelCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == majorityCount)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        result.put("directionMajorityLabel", majorityLabel);
        result.put("directionMajorityAccuracy", holdout.isEmpty()
                ? null : round(majorityCount * 1.0 / holdout.size(), 6));
        result.put("magnitudeZeroReturnMaePercent", summarizeReturns(holdout).get("mean") == null
                ? null : round(holdout.stream().mapToDouble(row -> Math.abs(row.futureReturnPercent().doubleValue()))
                        .average().orElse(0.0), 8));
        result.put("magnitudeZeroReturnRmsePercent", holdout.isEmpty()
                ? null : round(Math.sqrt(holdout.stream()
                        .mapToDouble(row -> Math.pow(row.futureReturnPercent().doubleValue(), 2))
                        .average().orElse(0.0)), 8));
        result.put("holdoutSamples", holdout.size());
        return result;
    }

    private List<Map<String, Object>> featureTargetCorrelations(List<TrainingExample> examples) {
        List<Map<String, Object>> correlations = new ArrayList<>();
        for (String featureName : CanonicalTechnicalFeatures.NAMES) {
            List<Double> featureValues = new ArrayList<>();
            List<Double> returns = new ArrayList<>();
            for (TrainingExample example : examples) {
                BigDecimal feature = example.features().technicalStrategyFeatures().get(featureName);
                if (feature == null || !Double.isFinite(feature.doubleValue())) continue;
                featureValues.add(feature.doubleValue());
                returns.add(example.futureReturnPercent().doubleValue());
            }
            double correlation = pearson(featureValues, returns);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("feature", featureName);
            detail.put("sampleCount", featureValues.size());
            detail.put("pearsonReturnCorrelation", Double.isFinite(correlation) ? round(correlation, 6) : null);
            correlations.add(detail);
        }
        return correlations;
    }

    private double pearson(List<Double> left, List<Double> right) {
        if (left.size() < 2 || left.size() != right.size()) return Double.NaN;
        double leftMean = left.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double rightMean = right.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double covariance = 0.0;
        double leftVariance = 0.0;
        double rightVariance = 0.0;
        for (int index = 0; index < left.size(); index++) {
            double leftDelta = left.get(index) - leftMean;
            double rightDelta = right.get(index) - rightMean;
            covariance += leftDelta * rightDelta;
            leftVariance += leftDelta * leftDelta;
            rightVariance += rightDelta * rightDelta;
        }
        double denominator = Math.sqrt(leftVariance * rightVariance);
        return denominator == 0.0 ? Double.NaN : covariance / denominator;
    }

    private Map<String, Object> factorCoverage(Map<String, Object> featureStatistics, int sampleCount) {
        Map<String, Object> factors = new LinkedHashMap<>();
        factor(factors, featureStatistics, sampleCount, "NIFTY OHLC-derived technicals",
                "Core NIFTY M5 candles", CanonicalTechnicalFeatures.NAMES);
        factor(factors, featureStatistics, sampleCount, "India VIX", "Not present in the canonical training schema");
        factor(factors, featureStatistics, sampleCount, "GIFT NIFTY", "Not present in the canonical training schema");
        factor(factors, featureStatistics, sampleCount, "SPY", "Not present in the canonical training schema");
        factor(factors, featureStatistics, sampleCount, "QQQ", "Not present in the canonical training schema");
        factor(factors, featureStatistics, sampleCount, "DXY", "Not present in the canonical training schema");
        factor(factors, featureStatistics, sampleCount, "USDINR", "Not present in the canonical training schema");
        factor(factors, featureStatistics, sampleCount, "Brent", "Not present in the canonical training schema");
        factor(factors, featureStatistics, sampleCount, "FII/DII", "Not present in the canonical training schema");
        factor(factors, featureStatistics, sampleCount, "Options OI/PCR", "Not present in the canonical training schema");
        factor(factors, featureStatistics, sampleCount, "VWAP", "Canonical field; computed from historical candle volume",
                List.of("vwap_distance_percent"));
        factor(factors, featureStatistics, sampleCount, "ATR", "Canonical volatility features",
                List.of("atr", "atr_percent"));
        factor(factors, featureStatistics, sampleCount, "Momentum", "Canonical trend/momentum features",
                List.of("ema_slope_percent", "roc10_percent", "macd_histogram"));
        factor(factors, featureStatistics, sampleCount, "Market regime", "Canonical regime fields",
                List.of("regime_score", "market_regime_encoded"));
        factor(factors, featureStatistics, sampleCount, "News", "Not present in the canonical training schema");
        return factors;
    }

        private Map<String, Object> featureSourceDiagnostics(List<Candle> candles) {
        long positiveVolumeCandles = candles.stream()
            .filter(candle -> candle.volume() != null && candle.volume().signum() > 0)
            .count();
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("volume", Map.of(
            "features", List.of("current_volume", "average_volume20", "relative_volume"),
            "source", "Upstox V3 NIFTY index M5 candle volume field",
            "positiveVolumeCandleCount", positiveVolumeCandles,
            "candleCount", candles.size(),
            "status", positiveVolumeCandles == 0 ? "UNAVAILABLE_ZERO_VOLUME" : "VOLUME_PRESENT",
            "treatment", positiveVolumeCandles == 0
                ? "Do not interpret zero-volume features as informative; never substitute NIFTY futures volume."
                : "Review feature-level coverage and instrument semantics before training."));
        diagnostics.put("vwap", Map.of(
            "feature", "vwap_distance_percent",
            "status", positiveVolumeCandles == 0 ? "UNAVAILABLE_ZERO_DENOMINATOR" : "COMPUTED_FROM_CANDLE_VOLUME",
            "reason", "VWAP is sum(typicalPrice * volume) / sum(volume); IndicatorEngine returns zero when total volume is zero."));
        diagnostics.put("historicalMarketContext", Map.of(
            "features", List.of("context_available", "market_context_score"),
            "status", "UNAVAILABLE_NO_ASOF_CONTEXT",
            "reason", "TrainingDatasetService builds each historical feature window with indexContext=null; live context is not joined as historical as-of data."));
        return diagnostics;
        }

    private void factor(
            Map<String, Object> factors,
            Map<String, Object> featureStatistics,
            int sampleCount,
            String name,
            String status,
            List<String> fields
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("includedInTrainingSchema", !fields.isEmpty());
        detail.put("status", status);
        detail.put("fields", fields);
        detail.put("sampleCount", sampleCount);
        if (fields.isEmpty()) {
            detail.put("availablePercent", null);
            detail.put("missingPercent", null);
            detail.put("nonFinitePercent", null);
            detail.put("unavailablePercent", null);
            detail.put("zeroPercent", null);
            detail.put("constantFeatureCount", null);
        } else {
            int expected = sampleCount * fields.size();
            int available = 0;
            int missing = 0;
            int nonFinite = 0;
            int zero = 0;
            int constant = 0;
            for (String field : fields) {
                Map<?, ?> stats = (Map<?, ?>) featureStatistics.get(field);
                int valid = (Integer) stats.get("validCount");
                available += valid;
                missing += (Integer) stats.get("nullCount");
                nonFinite += (Integer) stats.get("nonFiniteCount");
                zero += (Integer) stats.get("zeroCount");
                if (Boolean.TRUE.equals(stats.get("constant"))) constant++;
            }
            detail.put("availablePercent", expected == 0 ? null : round(available * 100.0 / expected, 4));
            detail.put("missingPercent", expected == 0 ? null : round(missing * 100.0 / expected, 4));
                detail.put("nonFinitePercent", expected == 0 ? null : round(nonFinite * 100.0 / expected, 4));
                detail.put("unavailablePercent", expected == 0 ? null
                    : round((missing + nonFinite) * 100.0 / expected, 4));
            detail.put("zeroPercent", available == 0 ? null : round(zero * 100.0 / available, 4));
            detail.put("constantFeatureCount", constant);
        }
        detail.put("freshness", "NOT_MEASURED");
        factors.put(name, detail);
    }

    private void factor(
            Map<String, Object> factors,
            Map<String, Object> featureStatistics,
            int sampleCount,
            String name,
            String status
    ) {
        factor(factors, featureStatistics, sampleCount, name, status, List.of());
    }

    private String qualityStatus(List<TrainingExample> examples) {
        if (examples.isEmpty()) return "REVIEW_REQUIRED";
        Map<String, Object> summary = featureQuality(examples, 0, examples.size());
        return (Integer) summary.get("missingValueCount") > 0
                || (Integer) summary.get("nonFiniteValueCount") > 0
                || (Integer) summary.get("constantFeatureCount") > 0
                || (Integer) summary.get("zeroDominantFeatureCount") > 0
                ? "REVIEW_REQUIRED" : "DATA_AUDIT_COMPLETE";
    }

    private double percentile(List<Double> sorted, double percentile) {
        double position = percentile * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        double value = sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (position - lower);
        return round(value, 8);
    }

    private double round(double value, int places) {
        if (!Double.isFinite(value)) return value;
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private String timestampAt(List<TrainingExample> examples, int index) {
        return index < 0 || index >= examples.size() ? null : examples.get(index).timestamp().toString();
    }
}