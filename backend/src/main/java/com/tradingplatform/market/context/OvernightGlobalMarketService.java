package com.tradingplatform.market.context;

import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.NoUsableMarketQuoteException;
import com.tradingplatform.market.Quote;
import com.tradingplatform.market.UpstoxMarketDataProvider;
import com.tradingplatform.market.nse.NseMarketDataService;
import com.tradingplatform.persistence.OvernightMarketDataEntity;
import com.tradingplatform.persistence.OvernightMarketDataRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OvernightGlobalMarketService {

    private static final Logger log = LoggerFactory.getLogger(OvernightGlobalMarketService.class);
    private static final String PROVIDER = "UPSTOX";

    private final UpstoxMarketDataProvider upstox;
    private final OvernightInstrumentResolver instrumentResolver;
    private final UpstoxGlobalInstrumentProperties instruments;
    private final OvernightMarketDataRepository repository;
    private final NseMarketDataService nseMarketData;
    private final Clock clock;
    private final Duration globalIndexFreshness;
    private final Duration globalIndicatorFreshness;
    private final Duration providerTimestampFutureSkew;
    private final boolean staleFallbackEnabled;

    @Autowired
    public OvernightGlobalMarketService(
            UpstoxMarketDataProvider upstox,
            OvernightInstrumentResolver instrumentResolver,
            UpstoxGlobalInstrumentProperties instruments,
            OvernightMarketDataRepository repository,
            @Value("${trading.overnight.freshness.global-index-seconds:300}") long globalIndexFreshnessSeconds,
            @Value("${trading.overnight.freshness.global-indicator-seconds:900}") long globalIndicatorFreshnessSeconds,
            @Value("${trading.overnight.freshness.provider-future-skew-seconds:5}") long providerTimestampFutureSkewSeconds,
            @Value("${trading.overnight.stale-fallback-enabled:false}") boolean staleFallbackEnabled,
            NseMarketDataService nseMarketData
    ) {
        this(upstox, instrumentResolver, instruments, repository, globalIndexFreshnessSeconds,
                globalIndicatorFreshnessSeconds, providerTimestampFutureSkewSeconds,
                staleFallbackEnabled, Clock.systemUTC(), nseMarketData);
    }

    public OvernightGlobalMarketService(
            UpstoxMarketDataProvider upstox,
            OvernightInstrumentResolver instrumentResolver,
            UpstoxGlobalInstrumentProperties instruments,
            OvernightMarketDataRepository repository,
            long globalIndexFreshnessSeconds,
            long globalIndicatorFreshnessSeconds,
            long providerTimestampFutureSkewSeconds,
            boolean staleFallbackEnabled
    ) {
        this(upstox, instrumentResolver, instruments, repository, globalIndexFreshnessSeconds,
                globalIndicatorFreshnessSeconds, providerTimestampFutureSkewSeconds,
                staleFallbackEnabled, Clock.systemUTC(), null);
    }

    OvernightGlobalMarketService(
            UpstoxMarketDataProvider upstox,
            OvernightInstrumentResolver instrumentResolver,
            UpstoxGlobalInstrumentProperties instruments,
            OvernightMarketDataRepository repository,
            long globalIndexFreshnessSeconds,
            long globalIndicatorFreshnessSeconds,
            long providerTimestampFutureSkewSeconds,
            boolean staleFallbackEnabled,
            Clock clock
    ) {
        this(upstox, instrumentResolver, instruments, repository, globalIndexFreshnessSeconds,
                globalIndicatorFreshnessSeconds, providerTimestampFutureSkewSeconds,
                staleFallbackEnabled, clock, null);
    }

    OvernightGlobalMarketService(
            UpstoxMarketDataProvider upstox,
            OvernightInstrumentResolver instrumentResolver,
            UpstoxGlobalInstrumentProperties instruments,
            OvernightMarketDataRepository repository,
            long globalIndexFreshnessSeconds,
            long globalIndicatorFreshnessSeconds,
            long providerTimestampFutureSkewSeconds,
            boolean staleFallbackEnabled,
            Clock clock,
            NseMarketDataService nseMarketData
    ) {
        this.upstox = upstox;
        this.instrumentResolver = instrumentResolver;
        this.instruments = instruments;
        this.repository = repository;
        this.nseMarketData = nseMarketData;
        this.clock = clock;
        this.globalIndexFreshness = Duration.ofSeconds(globalIndexFreshnessSeconds);
        this.globalIndicatorFreshness = Duration.ofSeconds(globalIndicatorFreshnessSeconds);
        this.providerTimestampFutureSkew = Duration.ofSeconds(providerTimestampFutureSkewSeconds);
        this.staleFallbackEnabled = staleFallbackEnabled;
    }

    public OvernightRetrieveResponse retrieve(String requestedInstrument) {
        return retrieve(requestedInstrument, false);
    }

    public OvernightRetrieveResponse savedQuotes() {
        Instant now = clock.instant();
        List<OvernightRetrieveResult> results = new ArrayList<>(InstrumentDescriptor.values().length);
        for (InstrumentDescriptor descriptor : InstrumentDescriptor.values()) {
            OvernightMarketDataEntity entity = latestObservation(descriptor).orElse(null);
            if (entity == null) {
                results.add(unavailable(descriptor, descriptor.key(instruments),
                        "No persisted market data is available."));
                continue;
            }
            MarketSessionLifecycle.Assessment assessment = lifecycle(descriptor, entity, now);
            results.add(fromDatabase(descriptor, entity, "DATABASE", legacyStatus(entity, assessment),
                    lifecycleMessage(entity, assessment)));
        }
        return new OvernightRetrieveResponse(results);
    }

    public OvernightRetrieveResponse refreshAllFromProvider() {
        return retrieve(null, true);
    }

    @Scheduled(
            cron = "${trading.overnight.refresh-cron:0 0 18 * * MON-FRI}",
            zone = "Asia/Kolkata"
    )
    public void refreshAfterMarketClose() {
        OvernightRetrieveResponse response = refreshAllFromProvider();
        long successful = response.results().stream()
                .filter(result -> "FRESH".equals(result.status()) || "STALE".equals(result.status()))
                .count();
        log.info("[OVERNIGHT_SCHEDULED_REFRESH] completed=true successful={} total={}",
                successful, response.results().size());
    }

    private OvernightRetrieveResponse retrieve(String requestedInstrument, boolean forceProviderRefresh) {
        List<InstrumentDescriptor> selected;
        if (requestedInstrument == null || requestedInstrument.isBlank()) {
            selected = List.of(InstrumentDescriptor.values());
        } else {
            selected = List.of(InstrumentDescriptor.fromCode(requestedInstrument));
        }

        List<OvernightRetrieveResult> results = new ArrayList<>(selected.size());
        for (InstrumentDescriptor descriptor : selected) {
            log.info("[OVERNIGHT_RETRIEVE_START] instrument={}", descriptor.name());
            try {
                results.add(retrieveOne(descriptor, forceProviderRefresh));
            } catch (RuntimeException error) {
                String key = descriptor.key(instruments);
                log.error("[OVERNIGHT_RETRIEVE_RESULT] instrument={} source=DATABASE status=PROVIDER_ERROR "
                                + "reason={}",
                        descriptor.name(), safeMessage(error), error);
                results.add(providerError(descriptor, key, "DATABASE",
                        "Overnight database operation failed: " + safeMessage(error)));
            }
        }
        return new OvernightRetrieveResponse(results);
    }

    public OvernightGlobalSnapshot snapshot() {
        Instant now = clock.instant();
        List<OvernightQuote> quotes = new ArrayList<>(InstrumentDescriptor.values().length);
        for (InstrumentDescriptor descriptor : InstrumentDescriptor.values()) {
            OvernightMarketDataEntity entity = latestObservation(descriptor).orElse(null);
            if (entity == null) {
                quotes.add(unavailable(descriptor.key(instruments), "No persisted market data is available."));
                continue;
            }
            MarketSessionLifecycle.Assessment assessment = lifecycle(descriptor, entity, now);
            quotes.add(new OvernightQuote(
                    entity.provider,
                    entity.instrumentKey,
                    entity.lastPrice,
                    entity.closePrice,
                    entity.netChange,
                    entity.closePrice == null || entity.closePrice.signum() == 0 || entity.netChange == null
                            ? null
                            : entity.netChange.multiply(java.math.BigDecimal.valueOf(100))
                                    .divide(entity.closePrice, 6, java.math.RoundingMode.HALF_UP),
                    entity.providerTimestamp,
                    assessment.status() == MarketSessionLifecycle.Status.LIVE
                            || assessment.status() == MarketSessionLifecycle.Status.COLLECTED
                            ? OvernightQuote.Status.FRESH
                            : assessment.status() == MarketSessionLifecycle.Status.UNAVAILABLE
                                    || assessment.status() == MarketSessionLifecycle.Status.ERROR
                                    || assessment.status() == MarketSessionLifecycle.Status.NOT_CONFIGURED
                                            ? OvernightQuote.Status.UNAVAILABLE : OvernightQuote.Status.STALE,
                    lifecycleMessage(entity, assessment),
                    entity.retrievedAt,
                    assessment.status(),
                    assessment.sessionId(),
                    assessment.requiredSessionId()
            ));
        }
        return new OvernightGlobalSnapshot(now, quotes.get(0), quotes.get(1), quotes.get(2),
                quotes.get(3), quotes.get(4), quotes.get(5), quotes.get(6), quotes.get(7),
                quotes.get(8), quotes.get(9), quotes.get(10), quotes.get(11));
    }

    private OvernightRetrieveResult retrieveOne(InstrumentDescriptor descriptor, boolean forceProviderRefresh) {
        Instant now = clock.instant();
        OvernightMarketDataEntity databaseRecord;
        String instrumentKey = descriptor.key(instruments);
        try {
            databaseRecord = latest(descriptor).orElse(null);
            if (databaseRecord != null) {
                instrumentKey = databaseRecord.instrumentKey;
            }
            log.info("[OVERNIGHT_DB_LOOKUP] instrumentKey={} recordFound={} latestTimestamp={}",
                    instrumentKey, databaseRecord != null,
                    databaseRecord == null ? null : databaseRecord.providerTimestamp);
        } catch (RuntimeException error) {
            log.error("[OVERNIGHT_DB_LOOKUP] instrumentKey={} recordFound=false success=false reason={}",
                    instrumentKey, safeMessage(error), error);
            return providerError(descriptor, instrumentKey, "DATABASE",
                    "Overnight database lookup failed: " + safeMessage(error));
        }
        MarketSessionLifecycle.Assessment databaseLifecycle = databaseRecord == null
                ? null : lifecycle(descriptor, databaseRecord, now);
        boolean databaseFresh = databaseLifecycle != null
                && (databaseLifecycle.status() == MarketSessionLifecycle.Status.LIVE
                        || databaseLifecycle.status() == MarketSessionLifecycle.Status.COLLECTED);
        logFreshness(instrumentKey,
                databaseRecord == null ? null : databaseRecord.providerTimestamp, databaseFresh, now);
        if (databaseFresh && !forceProviderRefresh) {
            OvernightRetrieveResult result = fromDatabase(descriptor, databaseRecord, "DATABASE", "FRESH", null);
            logRetrieveResult(result);
            return result;
        }

        String nseFailure = null;
        if (nseMarketData != null && descriptor.supportedByNse()) {
            try {
                Quote quote = nseMarketData.overnightNseQuote(descriptor.name());
                ResolvedOvernightInstrument resolved = new ResolvedOvernightInstrument(
                        quote.instrumentKey(), "NSE", "NSE_API", descriptor.nseSymbol());
                validateCompleteNseQuote(quote, quote.instrumentKey());
                OvernightRetrieveResult result = persistRetrievedQuote(
                        descriptor, resolved, quote, "NSE", "NSE");
                logRetrieveResult(result);
                return result;
            } catch (RuntimeException error) {
                nseFailure = safeMessage(error);
                log.warn("[OVERNIGHT_NSE_FALLBACK] instrument={} quoteAvailable=false reason={}",
                        descriptor.name(), nseFailure);
            }
        }
        if (descriptor == InstrumentDescriptor.GOLD) {
            String message = nseFailure == null
                    ? "NSE Gold futures are not configured; no alternate contract quote will be used."
                    : "NSE Gold futures are unavailable: " + nseFailure;
            OvernightRetrieveResult result = dataUnavailable(descriptor, instrumentKey, message, "NSE", "NSE");
            logRetrieveResult(result);
            return result;
        }

        List<ResolvedOvernightInstrument> candidates;
        try {
            candidates = instrumentResolver.resolve(descriptor.name(), descriptor.key(instruments),
                    descriptor.segment());
            if (candidates == null || candidates.isEmpty()) {
                throw new IllegalStateException("No eligible instrument contracts were resolved.");
            }
        } catch (RuntimeException error) {
            String message = joinMessages(safeMessage(error), nseFailure == null
                    ? null : "NSE quote was unavailable: " + nseFailure);
            log.error("[OVERNIGHT_INSTRUMENT_RESOLVED] instrument={} resolved=false reason={}",
                    descriptor.name(), message, error);
            if (staleFallbackEnabled && databaseRecord != null) {
                OvernightRetrieveResult result = fromDatabase(descriptor, databaseRecord, "DATABASE", "STALE",
                        "Unable to resolve a current contract; showing the latest persisted snapshot. " + message);
                logRetrieveResult(result);
                return result;
            }
            OvernightRetrieveResult result = providerError(descriptor, instrumentKey, "UPSTOX", message);
            logRetrieveResult(result);
            return result;
        }

        ResolvedOvernightInstrument resolved = candidates.get(0);
        instrumentKey = resolved.instrumentKey();
        if (instrumentKey == null || instrumentKey.isBlank()) {
            String message = "No Upstox instrument key is configured.";
            logRetrieve(descriptor, instrumentKey, databaseRecord, false, false, false, false,
                    "DATA_NOT_AVAILABLE");
            OvernightRetrieveResult result = unavailable(descriptor, instrumentKey, message);
            logRetrieveResult(result);
            return result;
        }

        boolean providerSuccess = false;
        boolean persisted = false;
        try {
            Quote quote = null;
            RuntimeException providerFailure = null;
            boolean allResponsesHadNoUsableQuote = true;
            for (ResolvedOvernightInstrument candidate : candidates) {
                resolved = candidate;
                instrumentKey = resolved.instrumentKey();
                log.info("[OVERNIGHT_INSTRUMENT_RESOLVED] instrument={} instrumentKey={} exchange={} "
                                + "segment={} tradingSymbol={}",
                        descriptor.name(), instrumentKey, resolved.exchange(), resolved.segment(),
                        resolved.tradingSymbol());
                try {
                    quote = upstox.getOvernightQuote(toInstrument(descriptor, resolved));
                    validateQuote(quote, instrumentKey, descriptor);
                    break;
                } catch (RuntimeException error) {
                    quote = null;
                    providerFailure = error;
                    allResponsesHadNoUsableQuote &= error instanceof NoUsableMarketQuoteException;
                    log.warn("[OVERNIGHT_CONTRACT_UNAVAILABLE] instrument={} instrumentKey={} "
                                    + "tradingSymbol={} reason={}",
                            descriptor.name(), instrumentKey, resolved.tradingSymbol(), safeMessage(error));
                    if (!allResponsesHadNoUsableQuote) {
                        break;
                    }
                }
            }
            if (quote == null) {
                if (providerFailure == null) {
                    throw new IllegalStateException("No usable provider quote was returned.");
                }
                if (allResponsesHadNoUsableQuote) {
                    throw new NoUsableMarketQuoteException(
                            "Upstox returned no usable quote for any active contract. " + safeMessage(providerFailure));
                }
                throw providerFailure;
            }
            providerSuccess = true;
            OvernightRetrieveResult persistedResult = persistRetrievedQuote(
                    descriptor, resolved, quote, "UPSTOX", "UPSTOX");
            persisted = true;
            String contractMessage = candidates.indexOf(resolved) == 0 ? null
                    : "Earlier contracts had no usable quote; using " + resolved.exchange()
                            + " " + resolved.tradingSymbol() + ".";
            String message = joinMessages(contractMessage,
                    nseFailure == null ? null : "NSE quote was unavailable: " + nseFailure);
            OvernightRetrieveResult result = withMessage(persistedResult, message);
            logRetrieve(descriptor, instrumentKey, databaseRecord, false, true, true, true, result.status());
            logRetrieveResult(result);
            return result;
        } catch (RuntimeException error) {
            String message = safeMessage(error);
            boolean noUsableQuote = error instanceof NoUsableMarketQuoteException;
            log.error("[OVERNIGHT_RETRIEVE_FAILURE] instrument={} instrumentKey={} providerSuccess={} "
                            + "persisted={} reason={}",
                    descriptor.name(), instrumentKey, providerSuccess, persisted, message, error);
            saveProviderError(descriptor, resolved, message, noUsableQuote
                    ? OvernightMarketDataEntity.Status.DATA_NOT_AVAILABLE
                    : OvernightMarketDataEntity.Status.PROVIDER_ERROR);
            boolean canUseStale = staleFallbackEnabled && databaseRecord != null;
            String finalStatus = canUseStale ? "STALE" : noUsableQuote ? "DATA_NOT_AVAILABLE" : "PROVIDER_ERROR";
            logRetrieve(descriptor, instrumentKey, databaseRecord, databaseFresh, true,
                    providerSuccess, persisted, finalStatus);
            if (canUseStale) {
                OvernightRetrieveResult result = fromDatabase(descriptor, databaseRecord, "DATABASE", "STALE",
                        "Upstox retrieval failed; showing the latest persisted snapshot. " + message);
                logRetrieveResult(result);
                return result;
            }
            if (noUsableQuote) {
                OvernightRetrieveResult result = dataUnavailable(descriptor, instrumentKey, message);
                logRetrieveResult(result);
                return result;
            }
            OvernightRetrieveResult result = providerError(descriptor, instrumentKey,
                    providerSuccess ? "DATABASE" : "UPSTOX", message);
            logRetrieveResult(result);
            return result;
        }
    }

    private void logFreshness(String instrumentKey, Instant timestamp, boolean fresh, Instant now) {
        Long ageSeconds = timestamp == null ? null : Duration.between(timestamp, now).getSeconds();
        log.info("[OVERNIGHT_FRESHNESS] instrumentKey={} fresh={} ageSeconds={} providerTimestamp={}",
                instrumentKey, fresh, ageSeconds, timestamp);
    }

    private void saveProviderError(
            InstrumentDescriptor descriptor,
            ResolvedOvernightInstrument resolved,
            String message,
            OvernightMarketDataEntity.Status status
    ) {
        if (resolved.instrumentKey() == null || resolved.instrumentKey().isBlank()) {
            return;
        }
        try {
            OvernightMarketDataEntity errorEntity = repository.save(
                    errorEntity(descriptor, resolved.instrumentKey(), resolved.segment(), clock.instant(), message, status));
            log.info("[OVERNIGHT_DB_SAVE] instrumentKey={} saved=true databaseId={} status={}",
                    resolved.instrumentKey(), errorEntity.id, status);
        } catch (RuntimeException saveError) {
            log.error("[OVERNIGHT_DB_SAVE] instrumentKey={} saved=false status={} reason={}",
                    resolved.instrumentKey(), status, safeMessage(saveError), saveError);
        }
    }

    private static void logRetrieveResult(OvernightRetrieveResult result) {
        log.info("[OVERNIGHT_RETRIEVE_RESULT] instrument={} source={} status={}",
                result.instrument(), result.source(), result.status());
    }

    private void logRetrieve(
            InstrumentDescriptor descriptor,
            String instrumentKey,
            OvernightMarketDataEntity databaseRecord,
            boolean databaseFresh,
            boolean providerCalled,
            boolean providerSuccess,
            boolean persisted,
            String finalStatus
    ) {
        log.info("[OVERNIGHT_RETRIEVE] instrument={} instrumentKey={} databaseRecordFound={} "
                        + "databaseRecordTimestamp={} databaseFresh={} providerCalled={} provider={} "
                        + "providerSuccess={} persisted={} finalStatus={}",
                descriptor.name(), instrumentKey, databaseRecord != null,
                databaseRecord == null ? null : databaseRecord.providerTimestamp, databaseFresh,
                providerCalled, PROVIDER, providerSuccess, persisted, finalStatus);
    }

    private Optional<OvernightMarketDataEntity> latest(InstrumentDescriptor descriptor) {
        if (descriptor.domesticFutureContract()) {
            return repository.findFirstByInstrumentNameAndLastPriceIsNotNullOrderByRetrievedAtDescIdDesc(
                    descriptor.name());
        }
        if (descriptor.dynamicContract()) {
            return repository.findFirstByInstrumentNameAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
                    descriptor.name());
        }
        if (descriptor.providerOmitsTimestamp()) {
            return repository.findFirstByInstrumentNameAndLastPriceIsNotNullOrderByRetrievedAtDescIdDesc(
                    descriptor.name());
        }
        String key = descriptor.key(instruments);
        return key == null || key.isBlank() ? Optional.empty() : latestByKey(key);
    }

    private Optional<OvernightMarketDataEntity> latestObservation(InstrumentDescriptor descriptor) {
        return repository.findFirstByInstrumentNameOrderByRetrievedAtDescIdDesc(descriptor.name());
    }

    private Optional<OvernightMarketDataEntity> latestByKey(String key) {
        return repository.findFirstByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
                key);
    }

    private MarketSessionLifecycle.Assessment lifecycle(
            InstrumentDescriptor descriptor,
            OvernightMarketDataEntity entity,
            Instant now
    ) {
        if (descriptor.providerOmitsTimestamp() && entity.lastPrice != null
                && entity.lastPrice.signum() > 0 && entity.providerTimestamp == null) {
            return new MarketSessionLifecycle.Assessment(MarketSessionLifecycle.Status.STALE,
                    null, null, "Provider did not supply a source timestamp; freshness cannot be verified.");
        }
        if (entity.lastPrice != null && entity.lastPrice.signum() > 0
                && (entity.providerTimestamp == null || entity.netChange == null)) {
            return new MarketSessionLifecycle.Assessment(MarketSessionLifecycle.Status.STALE,
                    null, null, "Quote is incomplete and cannot be used in directional bias.");
        }
        boolean providerError = entity.status == OvernightMarketDataEntity.Status.PROVIDER_ERROR;
        boolean validData = entity.lastPrice != null && entity.lastPrice.signum() > 0
                && entity.netChange != null && entity.providerTimestamp != null;
        Instant assessmentTime = entity.providerTimestamp != null
                && entity.providerTimestamp.isAfter(now)
                && Duration.between(now, entity.providerTimestamp).compareTo(providerTimestampFutureSkew) <= 0
                        ? entity.providerTimestamp : now;
        return MarketSessionLifecycle.assess(descriptor.sessionDefinition(), assessmentTime,
                entity.providerTimestamp, validData, providerError, true);
    }

    private static String legacyStatus(
            OvernightMarketDataEntity entity,
            MarketSessionLifecycle.Assessment assessment
    ) {
        if (assessment.status() == MarketSessionLifecycle.Status.LIVE
                || assessment.status() == MarketSessionLifecycle.Status.COLLECTED) {
            return "FRESH";
        }
        if (assessment.status() == MarketSessionLifecycle.Status.UNAVAILABLE
                || assessment.status() == MarketSessionLifecycle.Status.ERROR
                || assessment.status() == MarketSessionLifecycle.Status.NOT_CONFIGURED) {
            return entity.status == OvernightMarketDataEntity.Status.PROVIDER_ERROR
                    ? "PROVIDER_ERROR" : "DATA_NOT_AVAILABLE";
        }
        return "STALE";
    }

    private static String lifecycleMessage(
            OvernightMarketDataEntity entity,
            MarketSessionLifecycle.Assessment assessment
    ) {
        if (entity.errorMessage != null && !entity.errorMessage.isBlank()) {
            return entity.errorMessage;
        }
        return assessment.status() == MarketSessionLifecycle.Status.LIVE ? null : assessment.detail();
    }

    private boolean isFresh(Instant providerTimestamp, InstrumentDescriptor descriptor, Instant now) {
        if (providerTimestamp == null) {
            return false;
        }
        Duration limit = descriptor.segment().equals("GLOBAL_INDEX")
                ? globalIndexFreshness : globalIndicatorFreshness;
        Duration age = Duration.between(providerTimestamp, now);
        if (age.isNegative()) {
            if (age.abs().compareTo(providerTimestampFutureSkew) > 0) {
                return false;
            }
            age = Duration.ZERO;
        }
        return age.compareTo(limit) <= 0;
    }

    private static void validateQuote(
            Quote quote,
            String instrumentKey,
            InstrumentDescriptor descriptor
    ) {
        boolean incompleteAllowed = descriptor.providerMayReturnIncompleteQuote();
        if (quote == null || !instrumentKey.equals(quote.instrumentKey())
                || quote.price() == null || quote.price().signum() <= 0
                || (!incompleteAllowed && (quote.change() == null || quote.timestamp() == null))) {
            throw new NoUsableMarketQuoteException("Provider returned an invalid market-data response.");
        }
    }

    private static void validateCompleteNseQuote(Quote quote, String instrumentKey) {
        if (quote == null || !instrumentKey.equals(quote.instrumentKey())
                || quote.price() == null || quote.price().signum() <= 0
                || quote.change() == null || quote.timestamp() == null) {
            throw new NoUsableMarketQuoteException(
                    "NSE response did not include a valid price, daily change, and provider timestamp.");
        }
    }

    private static Instrument toInstrument(InstrumentDescriptor descriptor, ResolvedOvernightInstrument resolved) {
        return new Instrument(resolved.instrumentKey(), descriptor.name(), descriptor.displayName(),
                resolved.exchange(), resolved.segment(), "", true);
    }

    private OvernightRetrieveResult persistRetrievedQuote(
            InstrumentDescriptor descriptor,
            ResolvedOvernightInstrument resolved,
            Quote quote,
            String provider,
            String source
    ) {
        Instant retrievedAt = clock.instant();
        boolean fresh = quote.change() != null && isFresh(quote.timestamp(), descriptor, retrievedAt);
        logFreshness(resolved.instrumentKey(), quote.timestamp(), fresh, retrievedAt);
        String freshnessMessage = fresh ? null
                : quote.timestamp() == null
                        ? provider + " returned a price without a source timestamp; freshness cannot be verified."
                        : quote.change() == null
                                ? provider + " returned no net change; the quote is incomplete and excluded from bias."
                                : "Provider data timestamp is stale.";
        OvernightMarketDataEntity entity = toEntity(descriptor, resolved, quote, retrievedAt, provider,
                fresh ? OvernightMarketDataEntity.Status.FRESH : OvernightMarketDataEntity.Status.STALE,
                freshnessMessage);
        MarketSessionLifecycle.Assessment assessment = lifecycle(descriptor, entity, retrievedAt);
        boolean sessionUsable = assessment.status() == MarketSessionLifecycle.Status.LIVE
                || assessment.status() == MarketSessionLifecycle.Status.COLLECTED;
        entity.status = sessionUsable
                ? OvernightMarketDataEntity.Status.FRESH : OvernightMarketDataEntity.Status.STALE;
        if (!sessionUsable && (entity.errorMessage == null || entity.errorMessage.isBlank())) {
            entity.errorMessage = assessment.detail();
        }
        try {
            entity = repository.save(entity);
            log.info("[OVERNIGHT_DB_SAVE] instrumentKey={} saved=true databaseId={} provider={}",
                    resolved.instrumentKey(), entity.id, provider);
        } catch (RuntimeException error) {
            log.error("[OVERNIGHT_DB_SAVE] instrumentKey={} saved=false provider={} reason={}",
                    resolved.instrumentKey(), provider, safeMessage(error), error);
            throw error;
        }
        String message = sessionUsable ? null
                : freshnessMessage == null ? assessment.detail() : freshnessMessage;
        return fromDatabase(descriptor, entity, source, sessionUsable ? "FRESH" : "STALE", message);
    }

    private static OvernightMarketDataEntity toEntity(
            InstrumentDescriptor descriptor,
            ResolvedOvernightInstrument resolved,
            Quote quote,
            Instant retrievedAt,
            String provider,
            OvernightMarketDataEntity.Status status,
            String error
    ) {
        OvernightMarketDataEntity entity = new OvernightMarketDataEntity();
        entity.instrumentName = descriptor.name();
        entity.instrumentKey = resolved.instrumentKey();
        entity.provider = provider;
        entity.segment = resolved.segment();
        entity.lastPrice = quote.price();
        entity.openPrice = quote.open();
        entity.highPrice = quote.high();
        entity.lowPrice = quote.low();
        entity.closePrice = quote.previousClose();
        entity.netChange = quote.change();
        entity.volume = quote.volume();
        entity.providerTimestamp = quote.timestamp();
        entity.sessionId = MarketSessionLifecycle.dataSessionId(
                descriptor.sessionDefinition(), quote.timestamp());
        entity.retrievedAt = retrievedAt;
        entity.status = status;
        entity.errorMessage = error;
        return entity;
    }

    private static OvernightMarketDataEntity errorEntity(
            InstrumentDescriptor descriptor,
            String key,
            String segment,
            Instant retrievedAt,
            String message,
            OvernightMarketDataEntity.Status status
    ) {
        OvernightMarketDataEntity entity = new OvernightMarketDataEntity();
        entity.instrumentName = descriptor.name();
        entity.instrumentKey = key;
        entity.provider = PROVIDER;
        entity.segment = segment;
        entity.retrievedAt = retrievedAt;
        entity.status = status;
        entity.errorMessage = message;
        return entity;
    }

    private OvernightRetrieveResult fromDatabase(
            InstrumentDescriptor descriptor,
            OvernightMarketDataEntity entity,
            String source,
            String status,
            String message
    ) {
        MarketSessionLifecycle.Assessment assessment = lifecycle(descriptor, entity, clock.instant());
        return new OvernightRetrieveResult(descriptor.name(), entity.instrumentKey, entity.provider,
                source, status, entity.providerTimestamp, entity.retrievedAt, entity.lastPrice,
                entity.openPrice, entity.highPrice, entity.lowPrice, entity.closePrice, entity.netChange,
                entity.volume, message, assessment.status(), assessment.sessionId(),
                assessment.requiredSessionId());
    }

    private static String joinMessages(String first, String second) {
        if (first == null || first.isBlank()) return second;
        if (second == null || second.isBlank()) return first;
        return first + " " + second;
    }

    private static OvernightRetrieveResult withMessage(OvernightRetrieveResult result, String message) {
        return new OvernightRetrieveResult(result.instrument(), result.instrumentKey(), result.provider(),
                result.source(), result.status(), result.dataTimestamp(), result.retrievedAt(),
                result.lastPrice(), result.open(), result.high(), result.low(), result.close(),
                result.netChange(), result.volume(), joinMessages(result.message(), message),
                result.lifecycleStatus(), result.sessionId(), result.requiredSessionId());
    }

    private static OvernightRetrieveResult unavailable(
            InstrumentDescriptor descriptor,
            String key,
            String message
    ) {
        return new OvernightRetrieveResult(descriptor.name(), key, PROVIDER, "NONE",
                "DATA_NOT_AVAILABLE", null, null, null, null, null, null, null, null, null, message,
                MarketSessionLifecycle.Status.UNAVAILABLE, null, null);
    }

    private static OvernightRetrieveResult dataUnavailable(
            InstrumentDescriptor descriptor,
            String key,
            String message
    ) {
        return dataUnavailable(descriptor, key, message, PROVIDER, PROVIDER);
    }

    private static OvernightRetrieveResult dataUnavailable(
            InstrumentDescriptor descriptor,
            String key,
            String message,
            String provider,
            String source
    ) {
        return new OvernightRetrieveResult(descriptor.name(), key, provider, source,
                "DATA_NOT_AVAILABLE", null, null, null, null, null, null, null, null, null, message,
                MarketSessionLifecycle.Status.UNAVAILABLE, null, null);
    }

    private static OvernightRetrieveResult providerError(
            InstrumentDescriptor descriptor,
            String key,
            String source,
            String message
    ) {
        return new OvernightRetrieveResult(descriptor.name(), key, PROVIDER, source,
                "PROVIDER_ERROR", null, null, null, null, null, null, null, null, null, message,
                MarketSessionLifecycle.Status.ERROR, null, null);
    }

    private static OvernightQuote unavailable(String key, String message) {
        return new OvernightQuote(PROVIDER, key, null, null, null, null, null,
                OvernightQuote.Status.UNAVAILABLE, message, null,
                MarketSessionLifecycle.Status.UNAVAILABLE, null, null);
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        String safeMessage = message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
        return safeMessage.length() <= 1000 ? safeMessage : safeMessage.substring(0, 1000);
    }

    private enum InstrumentDescriptor {
        GIFT_NIFTY("Gift Nifty", "GLOBAL_INDEX"),
        SP500("S&P 500", "GLOBAL_INDEX"),
        US_TECH_100("US Tech 100", "GLOBAL_INDEX"),
        DOW("Dow Jones", "GLOBAL_INDEX"),
        NIKKEI("Nikkei 225", "GLOBAL_INDEX"),
        HANG_SENG("Hang Seng", "GLOBAL_INDEX"),
        USD_INR("USD/INR", "GLOBAL_INDICATOR"),
        CRUDE_OIL("NSE Crude Oil futures (proxy)", "GLOBAL_INDICATOR"),
        GOLD("NSE Gold futures (proxy)", "GLOBAL_INDICATOR"),
        FTSE_100("FTSE 100", "GLOBAL_INDEX"),
        DAX("DAX", "GLOBAL_INDEX"),
        BRENT("Brent crude", "GLOBAL_INDICATOR");

        private final String displayName;
        private final String segment;

        InstrumentDescriptor(String displayName, String segment) {
            this.displayName = displayName;
            this.segment = segment;
        }

        String displayName() {
            return displayName;
        }

        String segment() {
            return segment;
        }

        boolean dynamicContract() {
            return this == USD_INR || this == CRUDE_OIL || this == GOLD
                    || this == FTSE_100 || this == DAX;
        }

        boolean domesticFutureContract() {
            return this == USD_INR || this == CRUDE_OIL || this == GOLD;
        }

        boolean providerOmitsTimestamp() {
            return this == BRENT;
        }

        boolean supportedByNse() {
            return this == USD_INR || this == BRENT || this == CRUDE_OIL || this == GOLD;
        }

        String nseSymbol() {
            return switch (this) {
                case USD_INR -> "USDINR";
                case BRENT -> "BRCRUDEOIL";
                case CRUDE_OIL -> "CRUDEOIL";
                case GOLD -> "GOLD";
                default -> throw new IllegalStateException("No NSE symbol configured for " + this);
            };
        }

        boolean providerMayReturnIncompleteQuote() {
            return this == USD_INR || this == CRUDE_OIL || this == GOLD || this == BRENT;
        }

        MarketSessionDefinition sessionDefinition() {
            String market;
            String timezone;
            String open;
            String close;
            switch (this) {
                case GIFT_NIFTY -> {
                    market = "GIFT_NIFTY";
                    timezone = "Asia/Kolkata";
                    open = "06:30";
                    close = "15:40";
                }
                case SP500, US_TECH_100, DOW -> {
                    market = "US_EQUITIES";
                    timezone = "America/New_York";
                    open = "09:30";
                    close = "16:00";
                }
                case NIKKEI -> {
                    market = "NIKKEI";
                    timezone = "Asia/Tokyo";
                    open = "09:00";
                    close = "15:30";
                }
                case HANG_SENG -> {
                    market = "HANG_SENG";
                    timezone = "Asia/Hong_Kong";
                    open = "09:30";
                    close = "16:00";
                }
                case USD_INR -> {
                    market = "USD_INR_FUTURES";
                    timezone = "Asia/Kolkata";
                    open = "09:00";
                    close = "17:00";
                }
                case CRUDE_OIL, GOLD -> {
                    market = name() + "_FUTURES";
                    timezone = "Asia/Kolkata";
                    open = "09:00";
                    close = this == GOLD ? "23:55" : "23:30";
                }
                case FTSE_100 -> {
                    market = "FTSE_100";
                    timezone = "Europe/London";
                    open = "08:00";
                    close = "16:30";
                }
                case DAX -> {
                    market = "DAX";
                    timezone = "Europe/Berlin";
                    open = "09:00";
                    close = "17:30";
                }
                case BRENT -> {
                    market = "BRENT";
                    timezone = "Europe/London";
                    open = "01:00";
                    close = "23:00";
                }
                default -> throw new IllegalStateException("Missing market session for " + this);
            }
            return new MarketSessionDefinition(market, java.time.ZoneId.of(timezone),
                    java.time.LocalTime.parse(open), java.time.LocalTime.parse(close),
                    java.time.ZoneId.of("Asia/Kolkata"), java.time.LocalTime.of(15, 30),
                    true, 300);
        }

        String key(UpstoxGlobalInstrumentProperties config) {
            return switch (this) {
                case GIFT_NIFTY -> config.giftNifty();
                case SP500 -> config.sp500();
                case US_TECH_100 -> config.usTech100();
                case DOW -> config.dow();
                case NIKKEI -> config.nikkei();
                case HANG_SENG -> config.hangSeng();
                case USD_INR -> config.usdInr();
                case CRUDE_OIL -> config.crudeOil();
                case BRENT -> config.brent();
                case GOLD, FTSE_100, DAX -> "";
            };
        }

        static InstrumentDescriptor fromCode(String code) {
            try {
                return valueOf(code.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Unsupported overnight instrument: " + code);
            }
        }
    }
}
