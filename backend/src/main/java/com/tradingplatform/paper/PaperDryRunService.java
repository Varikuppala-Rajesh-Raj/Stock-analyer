package com.tradingplatform.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.Timeframe;
import com.tradingplatform.market.options.OptionContractSelector;
import com.tradingplatform.persistence.NseFuturesObservationEntity;
import com.tradingplatform.persistence.NseFuturesObservationRepository;
import com.tradingplatform.persistence.OptionMarketSnapshotEntity;
import com.tradingplatform.persistence.OptionMarketSnapshotRepository;
import com.tradingplatform.signal.AnalysisService;
import com.tradingplatform.signal.Signal;
import com.tradingplatform.signal.SignalResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Replays the deterministic NIFTY paper-entry rules against persisted historical data.
 * This service deliberately has no dependency on the paper order engine or account ledger.
 */
@Service
public class PaperDryRunService {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Duration FUTURES_TOLERANCE = Duration.ofSeconds(90);
    private static final LocalTime SESSION_OPEN = LocalTime.of(9, 15);
    private static final LocalTime SESSION_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime FIRST_DECISION = LocalTime.of(9, 20);
    private static final LocalTime LAST_SPOT_CANDLE = LocalTime.of(15, 25);
    private static final int EXPECTED_DECISION_INTERVALS = 75;
    private static final int MINIMUM_COVERAGE_PERCENT = 95;
    private static final int MINIMUM_COVERED_INTERVALS =
            (EXPECTED_DECISION_INTERVALS * MINIMUM_COVERAGE_PERCENT + 99) / 100;
    private static final DateTimeFormatter SESSION_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final MarketDataService marketData;
    private final AnalysisService analysis;
    private final OptionContractSelector selector;
    private final OptionMarketSnapshotRepository optionSnapshots;
    private final NseFuturesObservationRepository futuresObservations;
    private final ObjectMapper json;
    private final BigDecimal maxStopLossPercent;
    private final BigDecimal rewardRiskMultiple;
    private final BigDecimal minRiskReward;
    private final BigDecimal maxEntryDeviationPercent;
    private final BigDecimal maxSpotDivergencePercent;
    private final BigDecimal maxLossPercent;
    private final BigDecimal maxExposurePercent;
    private final BigDecimal maxAllocation;
    private final BigDecimal maxDailyLoss;
    private final BigDecimal slippagePercent;
    private final BigDecimal brokerageRate;
    private final int maxOpenPositions;
    private final int maxTradesPerDay;
    private final int defaultLotSize;
    private final int maxHoldingMinutes;
    private final BigDecimal defaultVirtualCapital;

    public PaperDryRunService(
            MarketDataService marketData,
            AnalysisService analysis,
            OptionContractSelector selector,
            OptionMarketSnapshotRepository optionSnapshots,
            NseFuturesObservationRepository futuresObservations,
            ObjectMapper json,
            @Value("${trading.paper.automation.max-stop-loss-percent:30}") BigDecimal maxStopLossPercent,
            @Value("${trading.paper.automation.reward-risk-multiple:2}") BigDecimal rewardRiskMultiple,
            @Value("${trading.paper.automation.min-risk-reward:1.5}") BigDecimal minRiskReward,
            @Value("${trading.paper.automation.max-entry-deviation-percent:2}") BigDecimal maxEntryDeviationPercent,
            @Value("${trading.paper.automation.max-spot-divergence-percent:0.2}") BigDecimal maxSpotDivergencePercent,
            @Value("${trading.paper.automation.max-loss-percent:1}") BigDecimal maxLossPercent,
            @Value("${trading.paper.automation.max-exposure-percent:20}") BigDecimal maxExposurePercent,
            @Value("${trading.paper.max-allocation-per-trade:5000}") BigDecimal maxAllocation,
            @Value("${trading.paper.max-daily-loss:300}") BigDecimal maxDailyLoss,
            @Value("${trading.paper.slippage-percent:0}") BigDecimal slippagePercent,
            @Value("${trading.paper.charges.brokerage-rate:0.0003}") BigDecimal brokerageRate,
            @Value("${trading.paper.max-open-positions:10}") int maxOpenPositions,
            @Value("${trading.paper.max-trades-per-day:50}") int maxTradesPerDay,
            @Value("${trading.paper.dry-run.lot-size:65}") int defaultLotSize,
            @Value("${trading.paper.max-holding-minutes:240}") int maxHoldingMinutes,
            @Value("${trading.paper.initial-balance:10000}") BigDecimal defaultVirtualCapital
    ) {
        this.marketData = marketData;
        this.analysis = analysis;
        this.selector = selector;
        this.optionSnapshots = optionSnapshots;
        this.futuresObservations = futuresObservations;
        this.json = json;
        this.maxStopLossPercent = maxStopLossPercent;
        this.rewardRiskMultiple = rewardRiskMultiple;
        this.minRiskReward = minRiskReward;
        this.maxEntryDeviationPercent = maxEntryDeviationPercent;
        this.maxSpotDivergencePercent = maxSpotDivergencePercent;
        this.maxLossPercent = maxLossPercent;
        this.maxExposurePercent = maxExposurePercent;
        this.maxAllocation = maxAllocation;
        this.maxDailyLoss = maxDailyLoss;
        this.slippagePercent = slippagePercent;
        this.brokerageRate = brokerageRate;
        this.maxOpenPositions = maxOpenPositions;
        this.maxTradesPerDay = maxTradesPerDay;
        this.defaultLotSize = defaultLotSize;
        this.maxHoldingMinutes = maxHoldingMinutes;
        this.defaultVirtualCapital = defaultVirtualCapital;
    }

    public Map<String, Object> settings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("defaultVirtualCapital", defaultVirtualCapital);
        settings.put("defaultLotSize", defaultLotSize);
        settings.put("maxLossPercent", maxLossPercent);
        settings.put("maxExposurePercent", maxExposurePercent);
        settings.put("maxStopLossPercent", maxStopLossPercent);
        settings.put("rewardRiskMultiple", rewardRiskMultiple);
        settings.put("minRiskReward", minRiskReward);
        settings.put("maxAllocationPerTrade", maxAllocation);
        settings.put("maxDailyLoss", maxDailyLoss);
        settings.put("maxOpenPositions", maxOpenPositions);
        settings.put("maxTradesPerDay", maxTradesPerDay);
        settings.put("maxHoldingMinutes", maxHoldingMinutes);
        settings.putAll(selector.filterSettings());
        settings.put("slippagePercent", slippagePercent);
        settings.put("brokerageRate", brokerageRate);
        return settings;
    }

    public Map<String, Object> coverage(LocalDate date) {
        validateDate(date);
        return loadReplayData(date).coverage();
    }

    public Map<String, Object> run(LocalDate date, BigDecimal virtualCapital, Integer requestedLotSize) {
        validateDate(date);
        BigDecimal capital = virtualCapital == null ? defaultVirtualCapital : virtualCapital;
        int lotSize = requestedLotSize == null ? defaultLotSize : requestedLotSize;
        if (capital.signum() <= 0 || capital.scale() > 2) {
            throw new IllegalArgumentException("Virtual capital must be a positive amount with at most two decimal places.");
        }
        if (lotSize < 1 || lotSize > 100_000) {
            throw new IllegalArgumentException("Lot quantity must be between 1 and 100000.");
        }

        ReplayData replayData = loadReplayData(date);
        Map<String, Object> dataCoverage = replayData.coverage();
        if (!Boolean.TRUE.equals(dataCoverage.get("ready"))) {
            throw new HistoricalReplayDataUnavailableException((String) dataCoverage.get("note"));
        }
        List<OptionMarketSnapshotEntity> optionRows = replayData.optionRows().stream()
                .filter(row -> withinSession(row.timestamp, date))
                .toList();
        List<Candle> candles = replayData.candles();
        Instant sessionStart = sessionInstant(date, SESSION_OPEN);
        Instant sessionEnd = sessionInstant(date, SESSION_CLOSE);
        List<NseFuturesObservationEntity> futuresRows = replayData.futuresRows().stream()
                .filter(row -> !row.timestamp.isBefore(sessionStart) && !row.timestamp.isAfter(sessionEnd))
                .toList();

        NavigableMap<Instant, List<OptionMarketSnapshotEntity>> byTimestamp = new TreeMap<>();
        optionRows.forEach(row -> byTimestamp.computeIfAbsent(row.timestamp, ignored -> new ArrayList<>()).add(row));
        NavigableMap<Instant, NseFuturesObservationEntity> futures = new TreeMap<>();
        futuresRows.forEach(row -> futures.put(row.timestamp, row));

        List<Trade> trades = new ArrayList<>();
        List<Map<String, Object>> cycles = new ArrayList<>();
        int entryOrders = 0;
        int orderEvents = 0;
        int noTradeCycles = 0;
        int candlesUsed = 0;

        for (Instant timestamp : sessionSlots(date, SESSION_OPEN, SESSION_CLOSE)) {
            List<OptionMarketSnapshotEntity> snapshots = byTimestamp.get(timestamp);
            if (snapshots == null) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", null, null, null, null, null,
                        "HISTORICAL_OPTION_SNAPSHOT_UNAVAILABLE", null));
                continue;
            }
            Map<String, OptionMarketSnapshotEntity> quoteByInstrument = new LinkedHashMap<>();
            snapshots.forEach(row -> quoteByInstrument.put(row.instrumentKey, row));

            for (Trade trade : trades) {
                if (trade.closedAt != null) continue;
                OptionMarketSnapshotEntity quote = quoteByInstrument.get(trade.instrumentKey);
                if (quote == null) continue;
                trade.lastPrice = quote.ltp;
                trade.lastPriceAt = timestamp;
                BigDecimal mark = quote.ltp.subtract(trade.entryPrice).multiply(BigDecimal.valueOf(trade.quantity));
                trade.peakUnrealized = trade.peakUnrealized.max(mark);
                String exitReason = exitReason(trade, quote.ltp, timestamp);
                if (exitReason != null) {
                    close(trade, quote.ltp, timestamp, exitReason);
                    orderEvents++;
                }
            }

            Instant lastCompletedCandleAt = timestamp.minus(Duration.ofMinutes(5));
            List<Candle> asOfCandles = candles.stream()
                    .filter(candle -> !candle.timestamp().isAfter(lastCompletedCandleAt))
                    .toList();
            candlesUsed = Math.max(candlesUsed, asOfCandles.size());
            if (asOfCandles.size() < 60) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", null, null, null, null, null,
                        "INSUFFICIENT_NIFTY_CANDLE_HISTORY (need 60 five-minute candles)", null));
                continue;
            }

            SignalResult signal = analysis.analyze("NIFTY", Timeframe.M5, asOfCandles);
            var currentFuture = futures.floorEntry(timestamp);
            var priorFuture = futures.floorEntry(timestamp.minus(Duration.ofMinutes(5)));
            Optional<Candle> currentSpot = candleAtOrBefore(asOfCandles, lastCompletedCandleAt);
            Optional<Candle> priorSpot = candleAtOrBefore(asOfCandles,
                    lastCompletedCandleAt.minus(Duration.ofMinutes(5)));
            if (currentFuture == null || priorFuture == null
                    || !fresh(currentFuture.getKey(), timestamp, FUTURES_TOLERANCE)
                    || !fresh(priorFuture.getKey(), timestamp.minus(Duration.ofMinutes(5)), FUTURES_TOLERANCE)
                    || currentFuture.getKey().isBefore(sessionStart)
                    || priorFuture.getKey().isBefore(sessionStart)
                    || currentSpot.isEmpty() || priorSpot.isEmpty()) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", null, null, null, null, signal.signalStrength(),
                        "HISTORICAL_FUTURES_OR_SPOT_CONFIRMATION_UNAVAILABLE", null));
                continue;
            }

            NseFuturesObservationEntity current = currentFuture.getValue();
            NseFuturesObservationEntity prior = priorFuture.getValue();
            if (!current.identifier.equals(prior.identifier) || !current.expiry.equals(prior.expiry)
                    || !current.timestamp.atZone(INDIA).toLocalDate().equals(prior.timestamp.atZone(INDIA).toLocalDate())) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", null, null, null, null, signal.signalStrength(),
                        "FUTURES_CONTRACT_ROLLOVER_OR_SESSION_BOUNDARY", null));
                continue;
            }
            double futuresReturn = percentage(current.lastPrice, prior.lastPrice);
            double spotReturn = percentage(currentSpot.get().close(), priorSpot.get().close());
            double futuresLead = futuresReturn - spotReturn;

            String direction = direction(signal, futuresReturn, futuresLead);
            if (direction == null) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", null, null, null, null, signal.signalStrength(),
                        "NO_CLEAR_EDGE_OR_FUTURES_DIVERGENCE", null));
                continue;
            }
            String optionType = "UP".equals(direction) ? "CE" : "PE";
            Optional<LocalDate> selectedExpiry = snapshots.stream()
                    .map(row -> row.expiry)
                    .filter(expiry -> !expiry.isBefore(date))
                    .min(Comparator.naturalOrder());
            if (selectedExpiry.isEmpty()) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, null, null,
                        signal.signalStrength(), "NO_UNEXPIRED_OPTION_EXPIRY_IN_SNAPSHOT", null));
                continue;
            }
            List<OptionMarketSnapshotEntity> expiryRows = snapshots.stream()
                    .filter(row -> row.expiry.equals(selectedExpiry.get()))
                    .toList();
            String expiry = selectedExpiry.get().toString();
            JsonNode chain = chain(expiryRows);
            OptionContractSelector.Contract contract = selector.select(chain, optionType).orElse(null);
            if (contract == null) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, null, null,
                        signal.signalStrength(), "NO_LIQUID_OPTION_CONTRACT_PASSED_SPREAD_VOLUME_OI_ATM_AND_DELTA_FILTERS",
                        null));
                continue;
            }
            Map<String, Object> selection = selection(contract);

            if (Math.abs(contract.spot().doubleValue() - currentSpot.get().close().doubleValue())
                    / currentSpot.get().close().doubleValue() * 100 > maxSpotDivergencePercent.doubleValue()) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, contract.strike(),
                        expiry, signal.signalStrength(),
                        "OPTION_CHAIN_AND_HISTORICAL_SPOT_DIVERGE", selection));
                continue;
            }

            OptionMarketSnapshotEntity selectedQuote = quoteByInstrument.get(contract.instrumentKey());
            if (selectedQuote == null) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, contract.strike(),
                        expiry, signal.signalStrength(),
                        "SELECTED_CONTRACT_QUOTE_MISSING_AT_DECISION_TIME", selection));
                continue;
            }
            if (contract.ltp().signum() <= 0 || selectedQuote.ltp == null
                    || percentDifference(contract.ltp(), selectedQuote.ltp).compareTo(maxEntryDeviationPercent) > 0) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, contract.strike(),
                        expiry, signal.signalStrength(),
                        "OPTION_PRICE_MOVED_BEYOND_ENTRY_TOLERANCE", selection));
                continue;
            }
            if (trades.stream().anyMatch(trade -> trade.closedAt == null
                    && trade.instrumentKey.equals(contract.instrumentKey()))) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, contract.strike(),
                        expiry, signal.signalStrength(),
                        "POSITION_ALREADY_OPEN_FOR_SELECTED_CONTRACT", selection));
                continue;
            }
            if (orderEvents + entryOrders >= maxTradesPerDay) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, contract.strike(),
                        expiry, signal.signalStrength(), "MAX_TRADES_PER_DAY_REACHED",
                        selection));
                continue;
            }
            long openCount = trades.stream().filter(trade -> trade.closedAt == null).count();
            if (openCount >= maxOpenPositions) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, contract.strike(),
                        expiry, signal.signalStrength(), "MAX_OPEN_POSITIONS_REACHED",
                        selection));
                continue;
            }

            BigDecimal entry = applySlippage(selectedQuote.ltp, true);
            BigDecimal equity = capital.add(realizedNet(trades)).add(unrealized(trades));
            if (equity.signum() <= 0) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, contract.strike(),
                        expiry, signal.signalStrength(), "NON_POSITIVE_DRY_RUN_EQUITY",
                        selection));
                continue;
            }
            BigDecimal stopDistance = entry.multiply(maxStopLossPercent).divide(BigDecimal.valueOf(100), 8,
                    RoundingMode.HALF_UP).min(equity.multiply(maxLossPercent).divide(BigDecimal.valueOf(100)
                    .multiply(BigDecimal.valueOf(lotSize)), 8, RoundingMode.HALF_UP));
            stopDistance = stopDistance.max(new BigDecimal("0.05"));
            BigDecimal stop = entry.subtract(stopDistance);
            if (stop.signum() <= 0) stop = entry.multiply(new BigDecimal("0.5"));
            BigDecimal target = entry.add(stopDistance.multiply(rewardRiskMultiple));
            BigDecimal notional = entry.multiply(BigDecimal.valueOf(lotSize));
            BigDecimal totalOpenCost = openCost(trades);
            double exposurePercent = notional.add(totalOpenCost).multiply(BigDecimal.valueOf(100))
                    .divide(equity, 8, RoundingMode.HALF_UP).doubleValue();
            BigDecimal riskPercent = stopDistance.multiply(BigDecimal.valueOf(lotSize))
                    .multiply(BigDecimal.valueOf(100)).divide(equity, 8, RoundingMode.HALF_UP);
            double riskReward = target.subtract(entry).divide(entry.subtract(stop), 8, RoundingMode.HALF_UP)
                    .doubleValue();
            String riskReason = riskRejectReason(equity, notional, totalOpenCost, exposurePercent, riskPercent,
                    riskReward, realizedNet(trades));
            if (riskReason != null) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, contract.strike(),
                        expiry, signal.signalStrength(), riskReason, selection));
                continue;
            }
            if (signal.target() == null || signal.entry() == null
                    || signal.target().subtract(signal.entry()).abs().signum() == 0) {
                noTradeCycles++;
                cycles.add(cycle(timestamp, "NO_TRADE", direction, optionType, contract.strike(),
                        expiry, signal.signalStrength(),
                        "NIFTY_SIGNAL_RISK_LEVELS_UNAVAILABLE", selection));
                continue;
            }

            Trade trade = new Trade();
            trade.timestamp = timestamp;
            trade.direction = direction;
            trade.optionType = optionType;
            trade.expiry = expiry;
            trade.strike = contract.strike();
            trade.instrumentKey = contract.instrumentKey();
            trade.quantity = lotSize;
            trade.entryPrice = entry;
            trade.entryCharges = charge(entry.multiply(BigDecimal.valueOf(lotSize)));
            trade.stop = stop;
            trade.target = target;
            trade.lastPrice = selectedQuote.ltp;
            trade.lastPriceAt = timestamp;
            trade.selection = selection;
            trade.riskReward = riskReward;
            trade.stopRiskPercent = riskPercent.doubleValue();
            trade.exposurePercent = exposurePercent;
            trade.signalStrength = signal.signalStrength();
            trade.reason = "TECHNICAL_SIGNAL_AND_FUTURES_CONFIRMATION";
            trades.add(trade);
            entryOrders++;
            cycles.add(cycle(timestamp, "TRADE_OPENED", direction, optionType, contract.strike(),
                    expiry, signal.signalStrength(),
                    "SELECTED: highest eligible option score after liquidity, spread, ATM-distance and delta filters",
                    selection));
        }

        BigDecimal totalCharges = charges(trades);
        BigDecimal realizedGross = realizedGross(trades);
        BigDecimal realized = realizedNet(trades);
        BigDecimal floating = unrealized(trades);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cycles", sessionSlots(date, SESSION_OPEN, SESSION_CLOSE).size());
        summary.put("tradeEntries", entryOrders);
        summary.put("closedTrades", trades.stream().filter(trade -> trade.closedAt != null).count());
        summary.put("openTrades", trades.stream().filter(trade -> trade.closedAt == null).count());
        summary.put("noTradeCycles", noTradeCycles);
        summary.put("winningClosedTrades", trades.stream().filter(trade -> trade.closedAt != null && trade.netPnl.signum() > 0).count());
        summary.put("losingClosedTrades", trades.stream().filter(trade -> trade.closedAt != null && trade.netPnl.signum() < 0).count());
        summary.put("realizedGrossPnl", realizedGross);
        summary.put("charges", totalCharges);
        summary.put("realizedNetPnl", realized);
        summary.put("unrealizedPnl", floating);
        summary.put("totalPnl", realized.add(floating));
        summary.put("returnPercent", realized.add(floating).multiply(BigDecimal.valueOf(100))
                .divide(capital, 4, RoundingMode.HALF_UP));
        summary.put("endingEquity", capital.add(realized).add(floating));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("optionSnapshotRows", optionRows.size());
        data.put("optionSnapshotIntervals", byTimestamp.size());
        data.put("futuresObservations", futuresRows.size());
        data.put("niftyCandles", candles.size());
        data.put("maxCandlesAvailableAtDecision", candlesUsed);
        data.put("coverage", dataCoverage);
        data.put("replayResolution", "Uses only recorded option-chain snapshot intervals (nominally 5 minutes).");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("generatedAt", Instant.now());
        result.put("virtualCapital", capital);
        result.put("lotSize", lotSize);
        result.put("settings", settings());
        result.put("data", data);
        result.put("summary", summary);
        result.put("trades", trades.stream().map(trade -> trade.toMap(brokerageRate)).toList());
        result.put("cycles", cycles);
        result.put("isHistoricalSimulation", true);
        result.put("note", "Results are simulated from saved snapshots; no paper account, order, or broker was accessed.");
        return result;
    }

    private ReplayData loadReplayData(LocalDate date) {
        Instant dayStart = date.atStartOfDay(INDIA).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(INDIA).toInstant().minusNanos(1);
        List<OptionMarketSnapshotEntity> options =
                optionSnapshots.findBySymbolAndTimestampBetweenOrderByTimestampAsc("NIFTY", dayStart, dayEnd);
        List<NseFuturesObservationEntity> futures =
                futuresObservations.findByTimestampBetweenOrderByTimestampAscIdAsc(dayStart, dayEnd);
        List<Candle> candles = marketData.persistedHistory("NIFTY", Timeframe.M5, date.minusDays(10), date);
        return new ReplayData(options, futures, candles, buildCoverage(date, options, futures, candles));
    }

    private Map<String, Object> buildCoverage(
            LocalDate date,
            List<OptionMarketSnapshotEntity> options,
            List<NseFuturesObservationEntity> futures,
            List<Candle> candles
    ) {
        List<Instant> decisionSlots = sessionSlots(date, FIRST_DECISION, SESSION_CLOSE);
        List<Instant> spotSlots = sessionSlots(date, SESSION_OPEN, LAST_SPOT_CANDLE);
        Set<Instant> optionTimes = new HashSet<>();
        options.stream()
                .map(row -> row.timestamp)
                .filter(timestamp -> withinSession(timestamp, date))
                .forEach(optionTimes::add);
        Set<Instant> spotTimes = new HashSet<>();
        candles.stream()
                .map(Candle::timestamp)
                .filter(timestamp -> !timestamp.isBefore(sessionInstant(date, SESSION_OPEN))
                        && !timestamp.isAfter(sessionInstant(date, LAST_SPOT_CANDLE)))
                .forEach(spotTimes::add);
        NavigableMap<Instant, NseFuturesObservationEntity> futuresByTimestamp = new TreeMap<>();
        futures.stream()
                .filter(row -> withinSession(row.timestamp, date))
                .forEach(row -> futuresByTimestamp.put(row.timestamp, row));

        List<String> missingOptions = missingSlots(decisionSlots, optionTimes::contains);
        List<String> missingSpotCandles = missingSlots(spotSlots, spotTimes::contains);
        List<String> missingFutures = missingSlots(decisionSlots,
                timestamp -> hasFuturesConfirmation(timestamp, date, futuresByTimestamp));
        List<String> missingSpotConfirmation = missingSlots(decisionSlots,
                timestamp -> hasSpotConfirmation(timestamp, date, candles));
        int optionDecisionIntervals = decisionSlots.size() - missingOptions.size();
        int futuresIntervals = decisionSlots.size() - missingFutures.size();
        int spotConfirmationIntervals = decisionSlots.size() - missingSpotConfirmation.size();
        int spotIntervals = spotSlots.size() - missingSpotCandles.size();
        boolean ready = meetsCoverageThreshold(optionDecisionIntervals)
                && meetsCoverageThreshold(futuresIntervals)
                && meetsCoverageThreshold(spotConfirmationIntervals)
                && meetsCoverageThreshold(spotIntervals);

        List<Instant> sessionOptionTimes = optionTimes.stream().sorted().toList();
        List<Instant> sessionSpotTimes = spotTimes.stream().sorted().toList();
        List<NseFuturesObservationEntity> futuresForDate = futures.stream()
                .filter(row -> withinSession(row.timestamp, date))
                .sorted(Comparator.comparing(row -> row.timestamp))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("timezone", INDIA.getId());
        result.put("status", ready ? "DATA_SUFFICIENT" : "DATA_INSUFFICIENT");
        result.put("ready", ready);
        result.put("expectedIntervals", decisionSlots.size());
        result.put("requiredIntervals", MINIMUM_COVERED_INTERVALS);
        result.put("minimumCoveragePercent", MINIMUM_COVERAGE_PERCENT);
        result.put("optionSnapshotRows", options.size());
        result.put("optionSnapshotIntervals", sessionOptionTimes.size());
        result.put("optionIntervals", optionDecisionIntervals);
        result.put("spotIntervals", spotIntervals);
        result.put("spotConfirmationIntervals", spotConfirmationIntervals);
        result.put("futuresObservations", futuresForDate.size());
        result.put("futuresIntervals", futuresIntervals);
        result.put("spotSource", "PERSISTED_M5_CANDLES");
        result.put("firstSnapshot", first(sessionOptionTimes));
        result.put("lastSnapshot", last(sessionOptionTimes));
        result.put("firstSpotCandle", first(sessionSpotTimes));
        result.put("lastSpotCandle", last(sessionSpotTimes));
        result.put("firstFuturesObservation", futuresForDate.isEmpty() ? null : futuresForDate.get(0).timestamp);
        result.put("lastFuturesObservation", futuresForDate.isEmpty()
                ? null : futuresForDate.get(futuresForDate.size() - 1).timestamp);
        result.put("missingOptionIntervals", missingOptions);
        result.put("missingSpotIntervals", missingSpotCandles);
        result.put("missingSpotConfirmationIntervals", missingSpotConfirmation);
        result.put("missingFuturesIntervals", missingFutures);
        result.put("coveragePercent", minimumCoveragePercent(optionDecisionIntervals, spotIntervals,
                spotConfirmationIntervals, futuresIntervals));
        result.put("note", coverageNote(ready, optionDecisionIntervals,
                spotIntervals, spotConfirmationIntervals, futuresIntervals));
        return result;
    }

    private boolean hasFuturesConfirmation(
            Instant timestamp,
            LocalDate date,
            NavigableMap<Instant, NseFuturesObservationEntity> futures
    ) {
        Instant previousTarget = timestamp.minus(Duration.ofMinutes(5));
        var currentEntry = futures.floorEntry(timestamp);
        var previousEntry = futures.floorEntry(previousTarget);
        if (currentEntry == null || previousEntry == null
                || currentEntry.getKey().isBefore(sessionInstant(date, SESSION_OPEN))
                || previousEntry.getKey().isBefore(sessionInstant(date, SESSION_OPEN))
                || !fresh(currentEntry.getKey(), timestamp, FUTURES_TOLERANCE)
                || !fresh(previousEntry.getKey(), previousTarget, FUTURES_TOLERANCE)) {
            return false;
        }
        NseFuturesObservationEntity current = currentEntry.getValue();
        NseFuturesObservationEntity previous = previousEntry.getValue();
        return current.identifier.equals(previous.identifier)
                && current.expiry.equals(previous.expiry)
                && current.timestamp.atZone(INDIA).toLocalDate().equals(date)
                && previous.timestamp.atZone(INDIA).toLocalDate().equals(date);
    }

    private boolean hasSpotConfirmation(Instant timestamp, LocalDate date, List<Candle> candles) {
        Instant currentTarget = timestamp.minus(Duration.ofMinutes(5));
        Instant previousTarget = currentTarget.minus(Duration.ofMinutes(5));
        Optional<Candle> current = candleAtOrBefore(candles, currentTarget);
        Optional<Candle> previous = candleAtOrBefore(candles, previousTarget);
        return current.filter(candle -> !candle.timestamp().isBefore(sessionInstant(date, SESSION_OPEN)))
                .filter(candle -> candle.timestamp().atZone(INDIA).toLocalDate().equals(date))
                .isPresent()
                && previous.filter(candle -> !candle.timestamp().isBefore(sessionInstant(date, SESSION_OPEN)))
                        .filter(candle -> candle.timestamp().atZone(INDIA).toLocalDate().equals(date))
                        .isPresent();
    }

    private List<String> missingSlots(List<Instant> expected, java.util.function.Predicate<Instant> available) {
        return expected.stream()
                .filter(timestamp -> !available.test(timestamp))
                .map(timestamp -> timestamp.atZone(INDIA).toLocalTime().format(SESSION_TIME))
                .toList();
    }

    private List<Instant> sessionSlots(LocalDate date, LocalTime start, LocalTime end) {
        Instant first = sessionInstant(date, start);
        Instant last = sessionInstant(date, end);
        List<Instant> slots = new ArrayList<>();
        for (Instant timestamp = first; !timestamp.isAfter(last); timestamp = timestamp.plus(Duration.ofMinutes(5))) {
            slots.add(timestamp);
        }
        return slots;
    }

    private Instant sessionInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(INDIA).toInstant();
    }

    private boolean withinSession(Instant timestamp, LocalDate date) {
        if (timestamp == null) return false;
        var local = timestamp.atZone(INDIA);
        LocalTime time = local.toLocalTime();
        return local.toLocalDate().equals(date)
                && !time.isBefore(SESSION_OPEN)
                && !time.isAfter(SESSION_CLOSE)
                && time.getSecond() == 0
                && time.getNano() == 0
                && time.getMinute() % 5 == 0;
    }

    private boolean meetsCoverageThreshold(int coveredIntervals) {
        return coveredIntervals >= MINIMUM_COVERED_INTERVALS;
    }

    private double minimumCoveragePercent(int... intervals) {
        int minimum = java.util.Arrays.stream(intervals).min().orElse(0);
        return Math.round(minimum * 1000.0 / EXPECTED_DECISION_INTERVALS) / 10.0;
    }

    private String coverageNote(
            boolean ready,
            int optionIntervals,
            int spotIntervals,
            int spotConfirmationIntervals,
            int futuresIntervals
    ) {
        String base = "Options " + optionIntervals + "/" + EXPECTED_DECISION_INTERVALS
                + ", NIFTY spot bars " + spotIntervals + "/" + EXPECTED_DECISION_INTERVALS
                + ", spot-confirmed intervals " + spotConfirmationIntervals + "/"
                + EXPECTED_DECISION_INTERVALS
                + ", futures-confirmed intervals " + futuresIntervals + "/"
                + EXPECTED_DECISION_INTERVALS + ". At least " + MINIMUM_COVERED_INTERVALS
                + "/" + EXPECTED_DECISION_INTERVALS + " intervals ("
                + MINIMUM_COVERAGE_PERCENT + "%) are required for each input.";
        return ready
                ? "DATA SUFFICIENT. " + base + " Missing observations remain missing and are never filled."
                : "DATA INSUFFICIENT. " + base + " Replay was not executed; missing observations are never fabricated.";
    }

    private Instant first(List<Instant> timestamps) {
        return timestamps.isEmpty() ? null : timestamps.get(0);
    }

    private Instant last(List<Instant> timestamps) {
        return timestamps.isEmpty() ? null : timestamps.get(timestamps.size() - 1);
    }

    private void validateDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("A replay date is required.");
        if (!date.isBefore(LocalDate.now(INDIA))) {
            throw new IllegalArgumentException("Choose a previous NSE calendar date; today and future dates cannot be replayed.");
        }
        if (date.isBefore(LocalDate.now(INDIA).minusYears(2))) {
            throw new IllegalArgumentException("Dry runs are limited to the previous two years of historical data.");
        }
    }

    private String direction(SignalResult signal, double futuresReturn, double futuresLead) {
        if (signal.signal() == Signal.BUY && signal.signalStrength() >= 65
                && futuresReturn > 0 && futuresLead >= 0) return "UP";
        if (signal.signal() == Signal.SELL && signal.signalStrength() >= 65
                && futuresReturn < 0 && futuresLead <= 0) return "DOWN";
        return null;
    }

    private String riskRejectReason(BigDecimal equity, BigDecimal notional, BigDecimal openCost,
                                    double exposurePercent, BigDecimal riskPercent, double riskReward,
                                    BigDecimal currentRealized) {
        if (currentRealized.compareTo(maxDailyLoss.negate()) <= 0) return "MAX_DAILY_LOSS_REACHED";
        if (notional.compareTo(maxAllocation) > 0) return "MAX_CAPITAL_ALLOCATION_PER_TRADE_EXCEEDED";
        if (notional.add(openCost).compareTo(equity) > 0) return "INSUFFICIENT_VIRTUAL_CASH";
        if (exposurePercent > maxExposurePercent.doubleValue()) return "MAX_EXPOSURE_LIMIT_EXCEEDED";
        if (riskPercent.compareTo(maxLossPercent) > 0) return "MAX_LOSS_LIMIT_EXCEEDED";
        if (riskReward < minRiskReward.doubleValue()) return "RISK_REWARD_BELOW_MINIMUM";
        return null;
    }

    private String exitReason(Trade trade, BigDecimal ltp, Instant timestamp) {
        if (ltp.compareTo(trade.stop) <= 0) return "STOP_LOSS";
        if (ltp.compareTo(trade.target) >= 0) return "TARGET";
        if (!timestamp.isBefore(trade.timestamp.plus(Duration.ofMinutes(maxHoldingMinutes)))) return "MAX_HOLDING_TIME";
        return null;
    }

    private void close(Trade trade, BigDecimal marketPrice, Instant timestamp, String reason) {
        trade.closedAt = timestamp;
        trade.exitReason = reason;
        trade.exitPrice = applySlippage(marketPrice, false);
        trade.exitCharges = charge(trade.exitPrice.multiply(BigDecimal.valueOf(trade.quantity)));
        trade.grossPnl = trade.exitPrice.subtract(trade.entryPrice).multiply(BigDecimal.valueOf(trade.quantity));
        trade.netPnl = trade.grossPnl.subtract(trade.entryCharges).subtract(trade.exitCharges);
    }

    private ArrayNode chain(List<OptionMarketSnapshotEntity> snapshots) {
        ArrayNode rows = json.createArrayNode();
        Map<BigDecimal, ObjectNode> byStrike = new LinkedHashMap<>();
        for (OptionMarketSnapshotEntity snapshot : snapshots) {
            ObjectNode row = byStrike.computeIfAbsent(snapshot.strikePrice, strike -> {
                ObjectNode created = json.createObjectNode();
                created.put("strike_price", strike.doubleValue());
                created.put("underlying_spot_price", snapshot.underlyingSpot.doubleValue());
                return created;
            });
            ObjectNode option = row.putObject("CE".equals(snapshot.optionType) ? "call_options" : "put_options");
            option.put("instrument_key", snapshot.instrumentKey);
            ObjectNode market = option.putObject("market_data");
            put(market, "ltp", snapshot.ltp);
            put(market, "bid_price", snapshot.bid);
            put(market, "ask_price", snapshot.ask);
            put(market, "volume", snapshot.volume);
            put(market, "oi", snapshot.oi);
            ObjectNode greeks = option.putObject("option_greeks");
            put(greeks, "delta", snapshot.delta);
        }
        byStrike.values().forEach(rows::add);
        return rows;
    }

    private void put(ObjectNode node, String name, BigDecimal value) {
        if (value != null) node.put(name, value);
    }

    private Map<String, Object> selection(OptionContractSelector.Contract contract) {
        Map<String, Object> selected = new LinkedHashMap<>();
        selected.put("instrumentKey", contract.instrumentKey());
        selected.put("optionType", contract.optionType());
        selected.put("strike", contract.strike());
        selected.put("spot", contract.spot());
        selected.put("ltp", contract.ltp());
        selected.put("bid", contract.bid());
        selected.put("ask", contract.ask());
        selected.put("volume", contract.volume());
        selected.put("openInterest", contract.openInterest());
        selected.put("delta", contract.delta());
        selected.put("spreadPercent", contract.spreadPercent());
        selected.put("selectionScore", contract.score());
        selected.put("selectionReason", "Highest score among contracts passing the configured spread, volume, open-interest, ATM-distance, and delta filters.");
        return selected;
    }

    private Map<String, Object> cycle(Instant timestamp, String status, String direction, String optionType,
                                      BigDecimal strike, String expiry, Integer strength, String reason,
                                      Map<String, Object> selection) {
        Map<String, Object> cycle = new LinkedHashMap<>();
        cycle.put("timestamp", timestamp);
        cycle.put("status", status);
        cycle.put("direction", direction);
        cycle.put("optionType", optionType);
        cycle.put("strike", strike);
        cycle.put("expiry", expiry);
        cycle.put("signalStrength", strength);
        cycle.put("reason", reason);
        cycle.put("selectedContract", selection);
        return cycle;
    }

    private Optional<Candle> candleAtOrBefore(List<Candle> candles, Instant target) {
        return candles.stream().filter(candle -> !candle.timestamp().isAfter(target))
                .max(Comparator.comparing(Candle::timestamp))
                .filter(candle -> Duration.between(candle.timestamp(), target).compareTo(Duration.ofMinutes(5)) <= 0);
    }

    private boolean fresh(Instant source, Instant target, Duration tolerance) {
        Duration age = Duration.between(source, target);
        return !age.isNegative() && age.compareTo(tolerance) <= 0;
    }

    private double percentage(BigDecimal current, BigDecimal prior) {
        if (current == null || prior == null || prior.signum() <= 0) return 0;
        return current.subtract(prior).multiply(BigDecimal.valueOf(100))
                .divide(prior, 8, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal percentDifference(BigDecimal first, BigDecimal second) {
        if (first == null || second == null || first.signum() <= 0) return BigDecimal.valueOf(Double.MAX_VALUE);
        return first.subtract(second).abs().multiply(BigDecimal.valueOf(100))
                .divide(first, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal applySlippage(BigDecimal price, boolean buy) {
        BigDecimal fraction = slippagePercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal multiplier = buy ? BigDecimal.ONE.add(fraction) : BigDecimal.ONE.subtract(fraction);
        return price.multiply(multiplier).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal charge(BigDecimal turnover) {
        return turnover.multiply(brokerageRate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal openCost(List<Trade> trades) {
        return trades.stream().filter(trade -> trade.closedAt == null)
                .map(trade -> trade.entryPrice.multiply(BigDecimal.valueOf(trade.quantity)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal realizedGross(List<Trade> trades) {
        return trades.stream().filter(trade -> trade.closedAt != null)
                .map(trade -> trade.grossPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal charges(List<Trade> trades) {
        return trades.stream()
                .map(trade -> trade.entryCharges.add(trade.closedAt == null
                        ? charge(trade.lastPrice.multiply(BigDecimal.valueOf(trade.quantity)))
                        : trade.exitCharges))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal realizedNet(List<Trade> trades) {
        return trades.stream().filter(trade -> trade.closedAt != null)
                .map(trade -> trade.netPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal unrealized(List<Trade> trades) {
        return trades.stream().filter(trade -> trade.closedAt == null && trade.lastPrice != null)
                .map(trade -> trade.lastPrice.subtract(trade.entryPrice).multiply(BigDecimal.valueOf(trade.quantity))
                        .subtract(trade.entryCharges)
                        .subtract(charge(trade.lastPrice.multiply(BigDecimal.valueOf(trade.quantity)))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record ReplayData(
            List<OptionMarketSnapshotEntity> optionRows,
            List<NseFuturesObservationEntity> futuresRows,
            List<Candle> candles,
            Map<String, Object> coverage
    ) {}

    private static final class Trade {
        private Instant timestamp;
        private Instant closedAt;
        private Instant lastPriceAt;
        private String direction;
        private String optionType;
        private String expiry;
        private String instrumentKey;
        private String exitReason;
        private String reason;
        private BigDecimal strike;
        private BigDecimal entryPrice;
        private BigDecimal exitPrice;
        private BigDecimal lastPrice;
        private BigDecimal stop;
        private BigDecimal target;
        private BigDecimal entryCharges = BigDecimal.ZERO;
        private BigDecimal exitCharges = BigDecimal.ZERO;
        private BigDecimal grossPnl = BigDecimal.ZERO;
        private BigDecimal netPnl = BigDecimal.ZERO;
        private BigDecimal peakUnrealized = BigDecimal.ZERO;
        private int quantity;
        private int signalStrength;
        private double riskReward;
        private double stopRiskPercent;
        private double exposurePercent;
        private Map<String, Object> selection;

        private Map<String, Object> toMap(BigDecimal brokerageRate) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("openedAt", timestamp);
            result.put("closedAt", closedAt);
            result.put("direction", direction);
            result.put("optionType", optionType);
            result.put("expiry", expiry);
            result.put("strike", strike);
            result.put("instrumentKey", instrumentKey);
            result.put("quantity", quantity);
            result.put("entryPrice", entryPrice);
            result.put("stopLoss", stop);
            result.put("target", target);
            result.put("exitPrice", exitPrice);
            result.put("lastPrice", lastPrice);
            result.put("lastPriceAt", lastPriceAt);
            result.put("exitReason", exitReason == null ? "OPEN_AT_END_OF_REPLAY" : exitReason);
            BigDecimal floatingGross = lastPrice.subtract(entryPrice).multiply(BigDecimal.valueOf(quantity));
            BigDecimal estimatedExitCharge = closedAt == null
                    ? lastPrice.multiply(BigDecimal.valueOf(quantity))
                            .multiply(brokerageRate).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            result.put("grossPnl", closedAt == null ? floatingGross : grossPnl);
            result.put("charges", entryCharges.add(closedAt == null ? estimatedExitCharge : exitCharges));
            result.put("netPnl", closedAt == null
                    ? floatingGross.subtract(entryCharges).subtract(estimatedExitCharge) : netPnl);
            result.put("unrealizedPnl", closedAt == null
                    ? floatingGross.subtract(entryCharges).subtract(estimatedExitCharge) : BigDecimal.ZERO);
            result.put("peakUnrealizedPnl", peakUnrealized);
            result.put("signalStrength", signalStrength);
            result.put("riskReward", riskReward);
            result.put("stopRiskPercent", stopRiskPercent);
            result.put("exposurePercent", exposurePercent);
            result.put("entryReason", reason);
            result.put("selectedContract", selection);
            return result;
        }
    }
}
