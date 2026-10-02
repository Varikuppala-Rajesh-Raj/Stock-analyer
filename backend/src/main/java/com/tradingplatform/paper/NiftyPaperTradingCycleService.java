package com.tradingplatform.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.MarketDataUnavailableException;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.nse.NseMarketContextService;
import com.tradingplatform.market.nse.NseMarketContextSnapshot;
import com.tradingplatform.market.options.NiftyOptionChainService;
import com.tradingplatform.market.options.OptionContractSelector;
import com.tradingplatform.market.scenario.ScenarioPaperTradeRequest;
import com.tradingplatform.persistence.PaperAutomationCycleEntity;
import com.tradingplatform.persistence.PaperAutomationCycleRepository;
import com.tradingplatform.signal.AnalysisService;
import com.tradingplatform.signal.Signal;
import com.tradingplatform.signal.SignalResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NiftyPaperTradingCycleService {
    private static final Logger log = LoggerFactory.getLogger(NiftyPaperTradingCycleService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private final MarketDataService marketData;
    private final AnalysisService analysis;
    private final NseMarketContextService contextService;
    private final NiftyOptionChainService optionChain;
    private final OptionContractSelector selector;
    private final PaperTradingEngine paper;
    private final PaperPriceService paperPrices;
    private final PaperTradingAutomationService automation;
    private final PaperAutomationCycleRepository cycleRepository;
    private final Duration maxContextAge;
    private final BigDecimal maxStopLossPercent;
    private final BigDecimal rewardRiskMultiple;
    private final BigDecimal minRiskReward;
    private final BigDecimal maxEntryDeviationPercent;
    private final BigDecimal maxSpotDivergencePercent;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile NiftyPaperTradingCycleResult lastResult =
            new NiftyPaperTradingCycleResult(Instant.EPOCH, "IDLE", "NOT_RUN", null, null, null, null, null, null);

    public NiftyPaperTradingCycleService(
            MarketDataService marketData,
            AnalysisService analysis,
            NseMarketContextService contextService,
            NiftyOptionChainService optionChain,
            OptionContractSelector selector,
            PaperTradingEngine paper,
            PaperPriceService paperPrices,
            PaperTradingAutomationService automation,
            PaperAutomationCycleRepository cycleRepository,
            @Value("${trading.paper.automation.max-context-age-seconds:60}") long maxContextAgeSeconds,
            @Value("${trading.paper.automation.max-stop-loss-percent:30}") BigDecimal maxStopLossPercent,
            @Value("${trading.paper.automation.reward-risk-multiple:2}") BigDecimal rewardRiskMultiple,
            @Value("${trading.paper.automation.min-risk-reward:1.5}") BigDecimal minRiskReward,
            @Value("${trading.paper.automation.max-entry-deviation-percent:2}") BigDecimal maxEntryDeviationPercent,
            @Value("${trading.paper.automation.max-spot-divergence-percent:0.2}") BigDecimal maxSpotDivergencePercent
    ) {
        this.marketData = marketData;
        this.analysis = analysis;
        this.contextService = contextService;
        this.optionChain = optionChain;
        this.selector = selector;
        this.paper = paper;
        this.paperPrices = paperPrices;
        this.automation = automation;
        this.cycleRepository = cycleRepository;
        this.maxContextAge = Duration.ofSeconds(maxContextAgeSeconds);
        this.maxStopLossPercent = maxStopLossPercent;
        this.rewardRiskMultiple = rewardRiskMultiple;
        this.minRiskReward = minRiskReward;
        this.maxEntryDeviationPercent = maxEntryDeviationPercent;
        this.maxSpotDivergencePercent = maxSpotDivergencePercent;
    }

    @Scheduled(fixedDelayString = "${trading.paper.automation.interval-ms:60000}")
    public void scheduledCycle() {
        if (!automation.isEnabled()) return;
        try {
            runOnce();
        } catch (RuntimeException exception) {
            log.error("NIFTY paper automation cycle failed", exception);
            lastResult = result("ERROR", exception.getMessage(), null, null, null, null, null);
        }
    }

    public NiftyPaperTradingCycleResult runOnce() {
        if (!running.compareAndSet(false, true)) {
            return result("SKIPPED", "CYCLE_ALREADY_RUNNING", null, null, null, null, null);
        }
        try {
            return runAndRecord();
        } finally {
            running.set(false);
        }
    }

    private NiftyPaperTradingCycleResult runAndRecord() {
        NiftyPaperTradingCycleResult result;
        try {
            result = evaluateOnce();
        } catch (RuntimeException exception) {
            log.error("NIFTY paper automation cycle failed", exception);
            result = result("ERROR", exception.getMessage(), null, null, null, null, null);
        }
        PaperAutomationCycleEntity audit = new PaperAutomationCycleEntity();
        audit.id = java.util.UUID.randomUUID();
        audit.timestamp = result.timestamp();
        audit.status = result.status();
        audit.reason = result.reason() == null ? "UNKNOWN" : result.reason();
        audit.direction = result.direction();
        audit.optionType = result.optionType();
        audit.expiry = result.expiry();
        audit.strike = result.strike();
        audit.signalStrength = result.signalStrength();
        audit.paperOrderId = result.order() == null ? null : result.order().id();
        cycleRepository.save(audit);
        lastResult = result;
        return result;
    }

    private NiftyPaperTradingCycleResult evaluateOnce() {
        Instant now = Instant.now();
        if (!automation.isEnabled()) {
            return result("DISABLED", "AUTOMATION_DISABLED", null, null, null, null, null);
        }
        if (!marketData.usesLiveProvider()) {
            return result("NO_TRADE", "LIVE_MARKET_DATA_REQUIRED", null, null, null, null, null);
        }
        var local = now.atZone(INDIA);
        if (local.getDayOfWeek().getValue() > 5
                || local.toLocalTime().isBefore(java.time.LocalTime.of(9, 15))
                || !local.toLocalTime().isBefore(java.time.LocalTime.of(15, 30))) {
            return result("NO_TRADE", "MARKET_CLOSED", null, null, null, null, null);
        }

        NseMarketContextSnapshot snapshot = contextService.snapshot();
        if (snapshot.nifty() == null || snapshot.futures() == null
                || snapshot.nifty().status() != NseMarketContextSnapshot.Status.AVAILABLE
                || snapshot.futures().status() != NseMarketContextSnapshot.Status.AVAILABLE
                || !fresh(snapshot.nifty().timestamp(), now)
                || !fresh(snapshot.futures().timestamp(), now)
                || snapshot.nifty().ltp() == null || snapshot.futures().ltp() == null
                || snapshot.futures().return5m() == null
                || snapshot.futures().return5m().status() != NseMarketContextSnapshot.Status.AVAILABLE
                || snapshot.futuresLead() == null
                || snapshot.futuresLead().status() != NseMarketContextSnapshot.Status.AVAILABLE) {
            return result("NO_TRADE", "NIFTY_OR_FUTURES_CONTEXT_STALE_OR_UNAVAILABLE",
                    null, null, null, null, null);
        }

        List<Candle> candles;
        try {
            candles = marketData.intraday("NIFTY", Timeframe.M5);
        } catch (MarketDataUnavailableException exception) {
            return result("NO_TRADE", "FRESH_NIFTY_CANDLES_UNAVAILABLE", null, null, null, null, null);
        }
        Duration candleAge = candles.isEmpty() ? null
                : Duration.between(candles.get(candles.size() - 1).timestamp(), now);
        if (candleAge == null || candleAge.isNegative() || candleAge.compareTo(maxContextAge) > 0) {
            return result("NO_TRADE", "NIFTY_CANDLES_STALE", null, null, null, null, null);
        }
        if (!withinPercent(candles.get(candles.size() - 1).close(),
                BigDecimal.valueOf(snapshot.nifty().ltp()), maxSpotDivergencePercent)) {
            return result("NO_TRADE", "NIFTY_CANDLE_AND_CONTEXT_PRICE_DIVERGE",
                    null, null, null, null, null);
        }

        SignalResult signal = analysis.analyze("NIFTY", Timeframe.M5, candles);
        double futuresLead = snapshot.futuresLead().value() == null ? 0 : snapshot.futuresLead().value();
        String direction;
        double futuresReturn = snapshot.futures().return5m().value() == null
                ? 0 : snapshot.futures().return5m().value();
        if (signal.signal() == Signal.BUY && signal.signalStrength() >= 65
                && futuresReturn > 0 && futuresLead >= 0) {
            direction = "UP";
        } else if (signal.signal() == Signal.SELL && signal.signalStrength() >= 65
                && futuresReturn < 0 && futuresLead <= 0) {
            direction = "DOWN";
        } else {
            return result("NO_TRADE", "NO_CLEAR_EDGE_OR_FUTURES_DIVERGENCE", null, null, null,
                    null, signal.signalStrength());
        }

        String optionType = "UP".equals(direction) ? "CE" : "PE";
        String expiry = optionChain.nearestNiftyExpiry()
                .orElse(null);
        if (expiry == null) return result("NO_TRADE", "NIFTY_EXPIRY_UNAVAILABLE",
                direction, optionType, null, null, signal.signalStrength());

        JsonNode chainResponse = optionChain.getNiftyOptionChain(expiry);
        if ("HISTORICAL_SNAPSHOT".equals(chainResponse.path("dataSource").asText())) {
            return result("NO_TRADE", "LIVE_OPTION_CHAIN_REQUIRED", direction, optionType,
                    expiry, null, signal.signalStrength());
        }
        var contract = selector.select(chainResponse, optionType).orElse(null);
        if (contract == null) return result("NO_TRADE", "NO_LIQUID_OPTION_CONTRACT",
                direction, optionType, expiry, null, signal.signalStrength());
        if (!withinPercent(contract.spot(), BigDecimal.valueOf(snapshot.nifty().ltp()),
                maxSpotDivergencePercent)) {
            return result("NO_TRADE", "OPTION_CHAIN_AND_NIFTY_SPOT_DIVERGE",
                    direction, optionType, expiry, contract.strike(), signal.signalStrength());
        }

        OptionalInt officialLot = optionChain.niftyOptionLotSize(expiry, contract.instrumentKey());
        if (officialLot.isEmpty()) return result("NO_TRADE", "OFFICIAL_OPTION_LOT_SIZE_UNAVAILABLE",
                direction, optionType, expiry, contract.strike(), signal.signalStrength());
        int quantity = officialLot.getAsInt();

        var instrument = marketData.instrument(contract.instrumentKey());
        PaperPriceService.Price freshOptionPrice = paperPrices.current(instrument);
        BigDecimal deviationPercent = freshOptionPrice.value().subtract(contract.ltp()).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(contract.ltp(), 6, RoundingMode.HALF_UP);
        if (deviationPercent.compareTo(maxEntryDeviationPercent) > 0) {
            return result("NO_TRADE", "OPTION_PRICE_MOVED_BEYOND_ENTRY_TOLERANCE",
                    direction, optionType, expiry, contract.strike(), signal.signalStrength());
        }

        PaperDtos.Account account = paper.account();
        BigDecimal accountEquity = account.initialBalance().add(account.totalPnl());
        if (accountEquity.signum() <= 0) return result("NO_TRADE", "NON_POSITIVE_PAPER_EQUITY",
                direction, optionType, expiry, contract.strike(), signal.signalStrength());
        BigDecimal entry = freshOptionPrice.value();
        BigDecimal maxLossBudget = accountEquity.multiply(BigDecimal.valueOf(0.01));
        BigDecimal stopDistance = entry.multiply(maxStopLossPercent)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                .min(maxLossBudget.divide(BigDecimal.valueOf(quantity), 8, RoundingMode.HALF_UP));
        stopDistance = stopDistance.max(BigDecimal.valueOf(0.05));
        BigDecimal stop = entry.subtract(stopDistance);
        if (stop.signum() <= 0) stop = entry.multiply(BigDecimal.valueOf(0.5));
        BigDecimal target = entry.add(stopDistance.multiply(rewardRiskMultiple));
        BigDecimal exposurePercent = entry.multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(100))
                .divide(accountEquity, 6, RoundingMode.HALF_UP);
        BigDecimal stopRiskPercent = stopDistance.multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(100))
                .divide(accountEquity, 6, RoundingMode.HALF_UP);
        double riskReward = target.subtract(entry).divide(entry.subtract(stop), 6, RoundingMode.HALF_UP)
                .doubleValue();
        if (riskReward < minRiskReward.doubleValue()) {
            return result("NO_TRADE", "RISK_REWARD_BELOW_MINIMUM", direction, optionType, expiry,
                    contract.strike(), signal.signalStrength());
        }
        double liquidityScore = Math.max(0, Math.min(1,
                0.7 + Math.min(0.3, contract.volume().doubleValue() / 10000.0 * 0.15
                        + contract.openInterest().doubleValue() / 100000.0 * 0.15)));
        if (signal.target() == null || signal.entry() == null) {
            return result("NO_TRADE", "NIFTY_SIGNAL_RISK_LEVELS_UNAVAILABLE", direction, optionType,
                    expiry, contract.strike(), signal.signalStrength());
        }
        BigDecimal expectedMove = signal.target().subtract(signal.entry()).abs();
        if (expectedMove.signum() <= 0) {
            return result("NO_TRADE", "NIFTY_SIGNAL_RISK_LEVELS_UNAVAILABLE", direction, optionType,
                    expiry, contract.strike(), signal.signalStrength());
        }

        ScenarioPaperTradeRequest request = new ScenarioPaperTradeRequest(
                "NIFTY", contract.instrumentKey(), optionType, contract.strike().stripTrailingZeros().toPlainString(),
                expiry, quantity, snapshot.nifty().ltp(), direction,
                expectedMove.doubleValue(), signal.target().doubleValue(),
                15, signal.signalStrength() / 100.0, "VALID", liquidityScore, riskReward,
                stopRiskPercent.doubleValue(), exposurePercent.doubleValue(), true, quantity,
                entry, stop, target);

        PaperTradingAutomationDecision decision = automation.runCycle(request);
        if (!decision.tradeSubmitted()) {
            return result("NO_TRADE", decision.reason(), direction, optionType, expiry,
                    contract.strike(), signal.signalStrength());
        }
        return new NiftyPaperTradingCycleResult(Instant.now(), "TRADE_SUBMITTED", decision.reason(),
                direction, optionType, expiry, contract.strike(), signal.signalStrength(), decision.order());
    }

    public Map<String, Object> status() {
        return Map.of("enabled", automation.isEnabled(), "running", running.get(), "lastCycle", lastResult);
    }

    public java.util.List<PaperAutomationCycleEntity> recentCycles() {
        return cycleRepository.findTop100ByOrderByTimestampDesc();
    }

    private boolean fresh(String sourceTimestamp, Instant now) {
        if (sourceTimestamp == null || sourceTimestamp.isBlank()) return false;
        try {
            Instant timestamp;
            try {
                timestamp = Instant.parse(sourceTimestamp);
            } catch (DateTimeParseException ignored) {
                timestamp = java.time.LocalDateTime.parse(sourceTimestamp,
                        new java.time.format.DateTimeFormatterBuilder()
                                .appendPattern("dd-MMM-yyyy HH:mm")
                                .optionalStart().appendPattern(":ss").optionalEnd()
                                .toFormatter(java.util.Locale.ENGLISH))
                        .atZone(INDIA).toInstant();
            }

            Duration age = Duration.between(timestamp, now);
            return !age.isNegative() && age.compareTo(maxContextAge) <= 0;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static boolean withinPercent(BigDecimal first, BigDecimal second, BigDecimal maximumPercent) {
        if (first == null || second == null || second.signum() <= 0) return false;
        BigDecimal divergence = first.subtract(second).abs().multiply(BigDecimal.valueOf(100))
                .divide(second, 6, RoundingMode.HALF_UP);
        return divergence.compareTo(maximumPercent) <= 0;
    }

    private NiftyPaperTradingCycleResult result(
            String status, String reason, String direction, String optionType, String expiry,
            BigDecimal strike, Integer signalStrength
    ) {
        return new NiftyPaperTradingCycleResult(Instant.now(), status, reason, direction, optionType,
                expiry, strike, signalStrength, null);
    }
}
