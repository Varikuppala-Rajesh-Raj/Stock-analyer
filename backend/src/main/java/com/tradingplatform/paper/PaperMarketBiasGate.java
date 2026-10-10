package com.tradingplatform.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.context.OvernightGlobalMarketService;
import com.tradingplatform.market.context.OvernightGlobalSnapshot;
import com.tradingplatform.market.context.OvernightQuote;
import com.tradingplatform.market.nse.NseMarketContextSnapshot;
import com.tradingplatform.persistence.NseReferenceSnapshotEntity;
import com.tradingplatform.persistence.NseReferenceSnapshotRepository;
import com.tradingplatform.signal.Signal;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaperMarketBiasGate {
    private static final Logger log = LoggerFactory.getLogger(PaperMarketBiasGate.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private record Evidence(String name, double score, boolean available) {}
    public record Group(String name, double score, String direction, int factors, int availableFactors) {}
    public record Assessment(
            boolean tradeReady,
            String direction,
            String label,
            double score,
            int dataQualityPct,
            int alignedGroups,
            int opposingGroups,
            String reason,
            List<Group> groups
    ) {}

    private final OvernightGlobalMarketService globalMarket;
    private final NseReferenceSnapshotRepository referenceSnapshots;
    private final ObjectMapper objectMapper;
    private final double gapThresholdPct;
    private final double flowNeutralThresholdCr;
    private final double futuresOiThresholdPct;
    private final double returnNeutralThresholdPct;
    private final double pcrSupportiveThreshold;
    private final double pcrBearishThreshold;
    private final int minimumDataQualityPct;
    private final int minimumAlignedGroups;
    private final int maximumOpposingGroups;
    private final double[] groupWeights;
    private final Duration vixMaxAge;
    private final Duration flowMaxAge;

    public PaperMarketBiasGate(
            OvernightGlobalMarketService globalMarket,
            NseReferenceSnapshotRepository referenceSnapshots,
            ObjectMapper objectMapper,
            @Value("${trading.market-bias.gap-threshold-pct:0.15}") double gapThresholdPct,
            @Value("${trading.market-bias.flow-neutral-threshold-cr:500}") double flowNeutralThresholdCr,
            @Value("${trading.market-bias.futures-oi-threshold-pct:0.1}") double futuresOiThresholdPct,
            @Value("${trading.market-bias.return-neutral-threshold-pct:0.05}") double returnNeutralThresholdPct,
            @Value("${trading.market-bias.pcr-supportive-threshold:1.1}") double pcrSupportiveThreshold,
            @Value("${trading.market-bias.pcr-bearish-threshold:0.9}") double pcrBearishThreshold,
            @Value("${trading.market-bias.minimum-data-quality-pct:70}") int minimumDataQualityPct,
            @Value("${trading.market-bias.minimum-aligned-groups:3}") int minimumAlignedGroups,
            @Value("${trading.market-bias.maximum-opposing-groups:1}") int maximumOpposingGroups,
            @Value("${trading.market-bias.group-weights.global:1}") double globalWeight,
            @Value("${trading.market-bias.group-weights.domestic:1}") double domesticWeight,
            @Value("${trading.market-bias.group-weights.technical:1}") double technicalWeight,
            @Value("${trading.market-bias.group-weights.options:1}") double optionsWeight,
            @Value("${trading.market-bias.group-weights.volatility:1}") double volatilityWeight,
            @Value("${trading.market-bias.vix-max-age-seconds:180}") long vixMaxAgeSeconds,
            @Value("${trading.market-bias.flow-max-age-days:7}") long flowMaxAgeDays
    ) {
        this.globalMarket = globalMarket;
        this.referenceSnapshots = referenceSnapshots;
        this.objectMapper = objectMapper;
        this.gapThresholdPct = gapThresholdPct;
        this.flowNeutralThresholdCr = flowNeutralThresholdCr;
        this.futuresOiThresholdPct = futuresOiThresholdPct;
        this.returnNeutralThresholdPct = returnNeutralThresholdPct;
        this.pcrSupportiveThreshold = pcrSupportiveThreshold;
        this.pcrBearishThreshold = pcrBearishThreshold;
        this.minimumDataQualityPct = minimumDataQualityPct;
        this.minimumAlignedGroups = minimumAlignedGroups;
        this.maximumOpposingGroups = maximumOpposingGroups;
        this.groupWeights = new double[]{globalWeight, domesticWeight, technicalWeight, optionsWeight, volatilityWeight};
        this.vixMaxAge = Duration.ofSeconds(vixMaxAgeSeconds);
        this.flowMaxAge = Duration.ofDays(flowMaxAgeDays);
    }

    public Assessment assess(
            NseMarketContextSnapshot context,
            Signal technicalSignal,
            int signalStrength,
            JsonNode optionChain,
            Instant now
    ) {
        List<Group> groups = new ArrayList<>();
        List<Evidence> all = new ArrayList<>();

        OvernightGlobalSnapshot global = globalMarket.snapshot();
        List<Evidence> globalEvidence = globalEvidence(global, context);
        groups.add(group("GLOBAL", globalEvidence));
        all.addAll(globalEvidence);

        NseReferenceSnapshotEntity flows = referenceSnapshots.findFirstByDatasetKeyOrderByRetrievedAtDesc("FII_DII")
                .orElse(null);
        NseReferenceSnapshotEntity vix = referenceSnapshots.findFirstByDatasetKeyOrderByRetrievedAtDesc("INDIA_VIX")
                .orElse(null);
        List<Evidence> domesticEvidence = domesticEvidence(context, flows, now);
        groups.add(group("DOMESTIC", domesticEvidence));
        all.addAll(domesticEvidence);

        List<Evidence> technicalEvidence = technicalEvidence(context, technicalSignal, signalStrength);
        groups.add(group("TECHNICAL", technicalEvidence));
        all.addAll(technicalEvidence);

        List<Evidence> optionsEvidence = optionsEvidence(optionChain, context);
        groups.add(group("OPTIONS", optionsEvidence));
        all.addAll(optionsEvidence);

        List<Evidence> volatilityEvidence = volatilityEvidence(context, vix, now);
        groups.add(group("RISK/VOLATILITY", volatilityEvidence));
        all.addAll(volatilityEvidence);

        int availableFactors = (int) all.stream().filter(Evidence::available).count();
        int dataQualityPct = all.isEmpty() ? 0 : (int) Math.round(availableFactors * 100.0 / all.size());
        List<Group> scoredGroups = groups.stream().filter(group -> group.factors() > 0).toList();
        double weights = 0;
        double weightedScore = 0;
        for (Group group : scoredGroups) {
            int index = groups.indexOf(group);
            double weight = Math.max(0, groupWeights[index]);
            weights += weight;
            weightedScore += group.score() * weight;
        }
        Double score = weights == 0 ? null : weightedScore / weights * 3;
        String direction = score == null || Math.abs(score) < 0.3 ? null : score > 0 ? "UP" : "DOWN";
        String label = biasLabel(score);
        int aligned = direction == null ? 0 : (int) groups.stream()
                .filter(group -> group.direction().equals(direction)).count();
        int opposing = direction == null ? 0 : (int) groups.stream()
                .filter(group -> (direction.equals("UP") && group.direction().equals("DOWN"))
                        || (direction.equals("DOWN") && group.direction().equals("UP"))).count();
        boolean signalAligns = direction != null
                && ((direction.equals("UP") && technicalSignal == Signal.BUY)
                || (direction.equals("DOWN") && technicalSignal == Signal.SELL));
        boolean tradeReady = score != null
                && dataQualityPct >= minimumDataQualityPct
                && aligned >= minimumAlignedGroups
                && opposing <= maximumOpposingGroups
                && signalAligns
                && signalStrength >= 60
                && !label.equals("MIXED");
        String reason = tradeReady
                ? "PAPER_BIAS_READY:" + label + ":QUALITY_" + dataQualityPct + ":ALIGNED_" + aligned + "_OF_5"
                : "MARKET_BIAS_NO_CLEAR_EDGE:score=" + (score == null ? "UNAVAILABLE" : String.format(Locale.ROOT, "%.2f", score))
                        + ",quality=" + dataQualityPct + "%,aligned=" + aligned + "/5,opposing=" + opposing
                        + ",signal=" + (signalAligns ? "ALIGNED" : "NOT_ALIGNED");
        return new Assessment(tradeReady, direction, label, score == null ? 0 : score,
                dataQualityPct, aligned, opposing, reason, groups);
    }

    private List<Evidence> globalEvidence(OvernightGlobalSnapshot snapshot, NseMarketContextSnapshot context) {
        List<Evidence> evidence = new ArrayList<>();
        Double giftGap = snapshot.giftNifty() != null && snapshot.giftNifty().status() == OvernightQuote.Status.FRESH
                && snapshot.giftNifty().value() != null && context.nifty() != null
                && context.nifty().previousClose() != null && context.nifty().previousClose() != 0
                ? (snapshot.giftNifty().value().doubleValue() / context.nifty().previousClose() - 1) * 100 : null;
        evidence.add(scored("GIFT NIFTY gap", giftGap, gapThresholdPct));
        evidence.add(quote("S&P 500", snapshot.sp500(), false));
        evidence.add(quote("US Tech 100", snapshot.usTech100(), false));
        evidence.add(quote("Dow", snapshot.dow(), false));
        evidence.add(quote("Nikkei", snapshot.nikkei(), false));
        evidence.add(quote("Hang Seng", snapshot.hangSeng(), false));
        evidence.add(quote("USD/INR", snapshot.usdInr(), true));
        evidence.add(quote("Crude oil", snapshot.crudeOil(), true));
        return evidence;
    }

    private Evidence quote(String name, OvernightQuote quote, boolean inverse) {
        if (quote == null || quote.status() != OvernightQuote.Status.FRESH || quote.changePct() == null) {
            return unavailable(name);
        }
        return scored(name, quote.changePct().doubleValue() * (inverse ? -1 : 1), returnNeutralThresholdPct);
    }

    private List<Evidence> domesticEvidence(
            NseMarketContextSnapshot context,
            NseReferenceSnapshotEntity flowSnapshot,
            Instant now
    ) {
        List<Evidence> evidence = new ArrayList<>();
        JsonNode flowPayload = parsePayload(flowSnapshot, "FII_DII");
        JsonNode flowRecords = flowPayload == null ? null : flowPayload.path("value");
        for (String category : List.of("FII/FPI", "DII")) {
            JsonNode row = findCategory(flowRecords, category);
            boolean freshReport = flowSnapshot != null && flowSnapshot.providerDate != null
                    && !flowSnapshot.providerDate.isAfter(now.atZone(INDIA).toLocalDate())
                    && Duration.between(flowSnapshot.providerDate.atStartOfDay(INDIA).toInstant(), now)
                            .compareTo(flowMaxAge) <= 0;
            Double net = row == null ? null : number(row.path("netValue"));
            evidence.add(freshReport ? scored(category, net, flowNeutralThresholdCr) : unavailable(category));
        }
        var futures = context == null ? null : context.futures();
        if (futures == null || futures.status() != NseMarketContextSnapshot.Status.AVAILABLE
                || futures.changePercent() == null || futures.percentChangeInOpenInterest() == null
                || Math.abs(futures.percentChangeInOpenInterest()) < futuresOiThresholdPct) {
            evidence.add(unavailable("Futures price + OI"));
        } else {
            boolean priceUp = futures.changePercent() > returnNeutralThresholdPct;
            boolean priceDown = futures.changePercent() < -returnNeutralThresholdPct;
            boolean oiUp = futures.percentChangeInOpenInterest() > 0;
            double score = !priceUp && !priceDown ? 0
                    : priceUp == oiUp ? (priceUp ? 1 : -1) : (priceUp ? 1 : -1);
            evidence.add(new Evidence("Futures price + OI", score, true));
        }
        Double basisPct = context == null || context.futures() == null || context.nifty() == null
                || context.nifty().ltp() == null || context.nifty().ltp() == 0 || context.futures().basis() == null
                ? null : context.futures().basis() / context.nifty().ltp() * 100;
        evidence.add(scored("Futures basis", basisPct, gapThresholdPct));
        evidence.add(context != null && context.nifty() != null
                ? scored("NIFTY price structure", context.nifty().changePercent(), returnNeutralThresholdPct)
                : unavailable("NIFTY price structure"));
        return evidence;
    }

    private List<Evidence> technicalEvidence(
            NseMarketContextSnapshot context,
            Signal signal,
            int strength
    ) {
        List<Evidence> evidence = new ArrayList<>();
        if (signal == Signal.BUY && strength >= 60) evidence.add(new Evidence("NIFTY strategy signal", 1, true));
        else if (signal == Signal.SELL && strength >= 60) evidence.add(new Evidence("NIFTY strategy signal", -1, true));
        else evidence.add(unavailable("NIFTY strategy signal"));
        var nifty = context == null ? null : context.nifty();
        evidence.add(metric("NIFTY 5-minute return", nifty == null ? null : nifty.return5m()));
        evidence.add(metric("NIFTY 15-minute return", nifty == null ? null : nifty.return15m()));
        evidence.add(nifty == null ? unavailable("Previous-close structure")
                : scored("Previous-close structure", nifty.changePercent(), returnNeutralThresholdPct));
        return evidence;
    }

    private Evidence metric(String name, NseMarketContextSnapshot.Metric metric) {
        if (metric == null || metric.status() != NseMarketContextSnapshot.Status.AVAILABLE) return unavailable(name);
        return scored(name, metric.value(), returnNeutralThresholdPct);
    }

    private List<Evidence> optionsEvidence(JsonNode chain, NseMarketContextSnapshot context) {
        List<Evidence> evidence = new ArrayList<>();
        JsonNode data = chain == null ? null : chain.path("data");
        if (data == null || !data.isArray() || data.isEmpty()
                || "HISTORICAL_SNAPSHOT".equals(chain.path("dataSource").asText())) {
            evidence.add(unavailable("Option PCR"));
            evidence.add(unavailable("Option OI structure"));
            return evidence;
        }
        double spot = context == null || context.nifty() == null || context.nifty().ltp() == null
                ? 0 : context.nifty().ltp();
        double callOi = 0, putOi = 0, callChange = 0, putChange = 0;
        double putStrike = Double.NaN, callStrike = Double.NaN, maxPut = -1, maxCall = -1;
        boolean hasChangeOi = false;
        for (JsonNode row : data) {
            double strike = row.path("strike_price").asDouble(Double.NaN);
            JsonNode call = row.path("call_options").path("market_data");
            JsonNode put = row.path("put_options").path("market_data");
            double currentCallOi = numberOrZero(call.path("oi"));
            double currentPutOi = numberOrZero(put.path("oi"));
            callOi += currentCallOi;
            putOi += currentPutOi;
            double callDelta = numberOrZero(call.path("change_oi"));
            double putDelta = numberOrZero(put.path("change_oi"));
            hasChangeOi |= call.has("change_oi") || put.has("change_oi");
            callChange += callDelta;
            putChange += putDelta;
            if (currentPutOi > maxPut) { maxPut = currentPutOi; putStrike = strike; }
            if (currentCallOi > maxCall) { maxCall = currentCallOi; callStrike = strike; }
        }
        double pcr = callOi == 0 ? Double.NaN : putOi / callOi;
        evidence.add(Double.isFinite(pcr)
                ? new Evidence("Option PCR", pcr >= pcrSupportiveThreshold ? 1
                        : pcr <= pcrBearishThreshold ? -1 : 0, true)
                : unavailable("Option PCR"));
        if (!Double.isFinite(putStrike) || !Double.isFinite(callStrike) || spot <= 0 || !hasChangeOi) {
            evidence.add(unavailable("Option OI structure"));
        } else if (putStrike < spot && callStrike > spot && putChange > Math.abs(callChange)) {
            evidence.add(new Evidence("Option OI structure", 1, true));
        } else if (putStrike > spot && callStrike < spot && callChange > Math.abs(putChange)) {
            evidence.add(new Evidence("Option OI structure", -1, true));
        } else {
            evidence.add(new Evidence("Option OI structure", 0, true));
        }
        return evidence;
    }

    private List<Evidence> volatilityEvidence(
            NseMarketContextSnapshot context,
            NseReferenceSnapshotEntity vixSnapshot,
            Instant now
    ) {
        JsonNode payload = parsePayload(vixSnapshot, "INDIA_VIX");
        boolean fresh = vixSnapshot != null && vixSnapshot.providerTimestamp != null
                && !vixSnapshot.providerTimestamp.isAfter(now)
                && Duration.between(vixSnapshot.providerTimestamp, now).compareTo(vixMaxAge) <= 0;
        var nifty = context == null ? null : context.nifty();
        Double vixChange = payload == null ? null : number(payload.path("change"));
        if (!fresh || vixChange == null || nifty == null || nifty.return5m() == null
                || nifty.return5m().status() != NseMarketContextSnapshot.Status.AVAILABLE
                || nifty.return5m().value() == null) {
            return List.of(unavailable("VIX + NIFTY interaction"));
        }
        double spotMove = nifty.return5m().value();
        double score = vixChange < 0 && spotMove > returnNeutralThresholdPct ? 1
                : vixChange > 0 && spotMove < -returnNeutralThresholdPct ? -1 : 0;
        return List.of(new Evidence("VIX + NIFTY interaction", score, true));
    }

    private Group group(String name, List<Evidence> evidence) {
        List<Evidence> available = evidence.stream().filter(Evidence::available).toList();
        if (available.isEmpty()) return new Group(name, 0, "UNAVAILABLE", evidence.size(), 0);
        double score = available.stream().mapToDouble(Evidence::score).average().orElse(0);
        String direction = score >= 0.25 ? "UP" : score <= -0.25 ? "DOWN" : "NEUTRAL";
        return new Group(name, score, direction, evidence.size(), available.size());
    }

    private static Evidence scored(String name, Double value, double threshold) {
        if (value == null || !Double.isFinite(value)) return unavailable(name);
        return new Evidence(name, value >= threshold ? 1 : value <= -threshold ? -1 : 0, true);
    }

    private static Evidence unavailable(String name) {
        return new Evidence(name, 0, false);
    }

    private JsonNode parsePayload(NseReferenceSnapshotEntity snapshot, String dataset) {
        if (snapshot == null || snapshot.payloadJson == null || snapshot.payloadJson.isBlank()) return null;
        try {
            return objectMapper.readTree(snapshot.payloadJson);
        } catch (IOException error) {
            log.warn("[PAPER-BIAS] Ignoring malformed {} snapshot id={}.", dataset, snapshot.id, error);
            return null;
        }
    }

    private static JsonNode findCategory(JsonNode records, String category) {
        if (records == null || !records.isArray()) return null;
        for (JsonNode row : records) {
            if (category.equals(row.path("category").asText())) return row;
        }
        return null;
    }

    private static Double number(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        if (value.isNumber()) return value.doubleValue();
        String text = value.asText().replace(",", "").trim();
        if (text.isEmpty()) return null;
        try {
            double parsed = Double.parseDouble(text);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double numberOrZero(JsonNode value) {
        Double parsed = number(value);
        return parsed == null ? 0 : parsed;
    }

    private static String biasLabel(Double score) {
        if (score == null || Math.abs(score) < 0.3) return "MIXED";
        if (score >= 2) return "STRONG BULLISH";
        if (score >= 1) return "BULLISH";
        if (score >= 0.3) return "SLIGHTLY BULLISH";
        if (score <= -2) return "STRONG BEARISH";
        if (score <= -1) return "BEARISH";
        return "SLIGHTLY BEARISH";
    }
}
