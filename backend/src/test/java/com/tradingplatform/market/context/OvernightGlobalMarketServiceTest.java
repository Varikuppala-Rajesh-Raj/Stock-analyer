package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.MarketDataUnavailableException;
import com.tradingplatform.market.NoUsableMarketQuoteException;
import com.tradingplatform.market.Quote;
import com.tradingplatform.market.UpstoxMarketDataProvider;
import com.tradingplatform.market.nse.NseMarketDataService;
import com.tradingplatform.persistence.OvernightMarketDataEntity;
import com.tradingplatform.persistence.OvernightMarketDataRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OvernightGlobalMarketServiceTest {

    private static final Instant TEST_NOW = Instant.parse("2026-10-08T10:00:00Z");
    private static final UpstoxGlobalInstrumentProperties INSTRUMENTS =
            new UpstoxGlobalInstrumentProperties(
                    "GLOBAL_INDEX|SGX NIFTY",
                    "GLOBAL_INDEX|^GSPC",
                    "GLOBAL_INDEX|IXIX",
                    "GLOBAL_INDEX|^DJI",
                    "GLOBAL_INDEX|^N225",
                    "GLOBAL_INDEX|^HSI",
                    "",
                    "",
                    "",
                    ""
            );

    private UpstoxMarketDataProvider upstox;
    private NseMarketDataService nseMarketData;
    private OvernightInstrumentResolver instrumentResolver;
    private OvernightMarketDataRepository repository;

    @BeforeEach
    void setUp() {
        upstox = Mockito.mock(UpstoxMarketDataProvider.class);
        nseMarketData = Mockito.mock(NseMarketDataService.class);
        instrumentResolver = Mockito.mock(OvernightInstrumentResolver.class);
        repository = Mockito.mock(OvernightMarketDataRepository.class);
        when(instrumentResolver.resolve(anyString(), org.mockito.ArgumentMatchers.nullable(String.class),
                anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            String configuredKey = invocation.getArgument(1);
            String fallbackSegment = invocation.getArgument(2);
            String key = configuredKey;
            String exchange = "GLOBAL";
            String segment = fallbackSegment;
            if ("USD_INR".equals(code)) {
                key = "NCD_FO|USDINR";
                exchange = "NSE";
                segment = "NCD_FO";
            } else if ("CRUDE_OIL".equals(code)) {
                key = "NSE_COM|CRUDEOIL";
                exchange = "NSE";
                segment = "NSE_COM";
            } else if ("GOLD".equals(code)) {
                key = "NSE_COM|GOLD";
                exchange = "NSE";
                segment = "NSE_COM";
            } else if ("FTSE_100".equals(code)) {
                key = "GLOBAL_INDEX|^FTSE";
            } else if ("DAX".equals(code)) {
                key = "GLOBAL_INDEX|^GDAXI";
            } else if ("BRENT".equals(code)) {
                key = "GLOBAL_INDICATOR|BZUSD";
                segment = "GLOBAL_INDICATOR";
            }
            return List.of(new ResolvedOvernightInstrument(key, exchange, segment, null));
        });
        when(repository.findFirstByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(repository.save(any(OvernightMarketDataEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void freshDatabaseRecordDoesNotCallUpstox() {
        OvernightMarketDataEntity stored = stored(TEST_NOW.minusSeconds(30));
        when(repository.findFirstByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
                INSTRUMENTS.giftNifty())).thenReturn(Optional.of(stored));

        OvernightRetrieveResult result = service(false).retrieve("GIFT_NIFTY").results().get(0);

        assertEquals("DATABASE", result.source());
        assertEquals("FRESH", result.status());
        assertEquals(stored.providerTimestamp, result.dataTimestamp());
        verify(upstox, never()).getOvernightQuote(any(Instrument.class));
        verify(repository, never()).save(any(OvernightMarketDataEntity.class));
    }

    @Test
    void savedQuotesReturnsPersistedDataWithoutCallingUpstox() {
        OvernightMarketDataEntity stored = stored(TEST_NOW.minusSeconds(30));
        when(repository.findFirstByInstrumentNameOrderByRetrievedAtDescIdDesc("GIFT_NIFTY"))
                .thenReturn(Optional.of(stored));

        OvernightRetrieveResult result = service(false).savedQuotes().results().get(0);

        assertEquals("DATABASE", result.source());
        assertEquals("FRESH", result.status());
        assertEquals(stored.lastPrice, result.lastPrice());
        verify(upstox, never()).getOvernightQuote(any(Instrument.class));
    }

    @Test
    void savedQuotesPreservesPersistedProviderErrors() {
        OvernightMarketDataEntity stored = new OvernightMarketDataEntity();
        stored.instrumentName = "GIFT_NIFTY";
        stored.instrumentKey = INSTRUMENTS.giftNifty();
        stored.provider = "UPSTOX";
        stored.status = OvernightMarketDataEntity.Status.PROVIDER_ERROR;
        stored.errorMessage = "Upstox request timed out.";
        stored.retrievedAt = TEST_NOW;
        when(repository.findFirstByInstrumentNameOrderByRetrievedAtDescIdDesc("GIFT_NIFTY"))
                .thenReturn(Optional.of(stored));

        OvernightRetrieveResult result = service(false).savedQuotes().results().get(0);

        assertEquals("PROVIDER_ERROR", result.status());
        assertEquals(MarketSessionLifecycle.Status.ERROR, result.lifecycleStatus());
        assertEquals("Upstox request timed out.", result.message());
    }

    @Test
    void snapshotExposesPersistedEuropeanAndBrentQuotes() {
        OvernightMarketDataEntity ftse = stored(TEST_NOW.minusSeconds(30));
        ftse.instrumentName = "FTSE_100";
        ftse.instrumentKey = "GLOBAL_INDEX|^FTSE";
        OvernightMarketDataEntity dax = stored(TEST_NOW.minusSeconds(30));
        dax.instrumentName = "DAX";
        dax.instrumentKey = "GLOBAL_INDEX|^GDAXI";
        OvernightMarketDataEntity brent = stored(TEST_NOW.minusSeconds(30));
        brent.instrumentName = "BRENT";
        brent.instrumentKey = "GLOBAL_INDICATOR|BZUSD";
        brent.providerTimestamp = null;
        when(repository.findFirstByInstrumentNameOrderByRetrievedAtDescIdDesc("FTSE_100"))
                .thenReturn(Optional.of(ftse));
        when(repository.findFirstByInstrumentNameOrderByRetrievedAtDescIdDesc("DAX"))
                .thenReturn(Optional.of(dax));
        when(repository.findFirstByInstrumentNameOrderByRetrievedAtDescIdDesc("BRENT"))
                .thenReturn(Optional.of(brent));

        OvernightGlobalSnapshot snapshot = service(false).snapshot();

        assertEquals("GLOBAL_INDEX|^FTSE", snapshot.ftse100().instrumentKey());
        assertEquals("GLOBAL_INDEX|^GDAXI", snapshot.dax().instrumentKey());
        assertEquals("GLOBAL_INDICATOR|BZUSD", snapshot.brent().instrumentKey());
        assertEquals(OvernightQuote.Status.STALE, snapshot.brent().status());
        assertNull(snapshot.brent().timestamp());
        assertEquals(brent.retrievedAt, snapshot.brent().retrievedAt());
        verify(upstox, never()).getOvernightQuote(any(Instrument.class));
    }

    @Test
    void scheduledRefreshFetchesFromUpstoxEvenWhenDatabaseSnapshotIsFresh() {
        OvernightMarketDataEntity stored = stored(TEST_NOW.minusSeconds(30));
        when(repository.findFirstByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
                INSTRUMENTS.giftNifty())).thenReturn(Optional.of(stored));
        when(upstox.getOvernightQuote(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument instrument = invocation.getArgument(0);
            return quote(instrument, TEST_NOW);
        });

        OvernightRetrieveResponse response = service(false).refreshAllFromProvider();

        assertEquals("UPSTOX", response.results().get(0).source());
        verify(upstox, times(11)).getOvernightQuote(any(Instrument.class));
    }

    @Test
    void missingDatabaseRecordFetchesAndPersistsProviderData() {
        Quote providerQuote = quote(INSTRUMENTS.giftNifty(), TEST_NOW.minusSeconds(3));
        when(upstox.getOvernightQuote(any(Instrument.class))).thenReturn(providerQuote);

        OvernightRetrieveResult result = service(false).retrieve("GIFT_NIFTY").results().get(0);

        assertEquals("UPSTOX", result.source());
        assertEquals("FRESH", result.status());
        assertEquals(providerQuote.timestamp(), result.dataTimestamp());
        verify(upstox).getOvernightQuote(any(Instrument.class));
        verify(repository).save(any(OvernightMarketDataEntity.class));
    }

    @Test
    void usesAndPersistsNseCurrencyQuoteBeforeTryingUpstox() {
        Quote nseQuote = new Quote("USDINR", "NSE_API|USDINR", new BigDecimal("83.25"),
                new BigDecimal("83.10"), new BigDecimal("83.30"), new BigDecimal("83.05"),
                new BigDecimal("83.10"), new BigDecimal("0.15"), new BigDecimal("0.18"),
                null, TEST_NOW.minusSeconds(5));
        when(nseMarketData.overnightNseQuote("USD_INR")).thenReturn(nseQuote);

        OvernightRetrieveResult result = serviceWithNse(false).retrieve("USD_INR").results().get(0);

        assertEquals("NSE", result.source());
        assertEquals("NSE", result.provider());
        assertEquals("NSE_API|USDINR", result.instrumentKey());
        assertEquals("FRESH", result.status());
        ArgumentCaptor<OvernightMarketDataEntity> saved =
                ArgumentCaptor.forClass(OvernightMarketDataEntity.class);
        verify(repository).save(saved.capture());
        assertEquals("NSE", saved.getValue().provider);
        verify(upstox, never()).getOvernightQuote(any(Instrument.class));
    }

    @Test
    void fallsBackToUpstoxWhenNseQuoteCannotBeRetrieved() {
        when(nseMarketData.overnightNseQuote("USD_INR"))
                .thenThrow(new IllegalStateException("NSE quote endpoint unavailable"));
        when(upstox.getOvernightQuote(any(Instrument.class))).thenAnswer(invocation ->
                quote((Instrument) invocation.getArgument(0), TEST_NOW.minusSeconds(5)));

        OvernightRetrieveResult result = serviceWithNse(false).retrieve("USD_INR").results().get(0);

        assertEquals("UPSTOX", result.source());
        assertEquals("FRESH", result.status());
        org.junit.jupiter.api.Assertions.assertTrue(result.message().contains("NSE quote was unavailable"));
        verify(upstox).getOvernightQuote(any(Instrument.class));
    }

    @Test
    void fallsBackToUpstoxWhenNseQuoteCannotBeVerifiedAsFresh() {
        Quote incompleteNseQuote = new Quote("USDINR", "NSE_API|USDINR", new BigDecimal("83.25"),
                null, null, null, new BigDecimal("83.10"), new BigDecimal("0.15"),
                new BigDecimal("0.18"), null, null);
        when(nseMarketData.overnightNseQuote("USD_INR")).thenReturn(incompleteNseQuote);
        when(upstox.getOvernightQuote(any(Instrument.class))).thenAnswer(invocation ->
                quote((Instrument) invocation.getArgument(0), TEST_NOW.minusSeconds(5)));

        OvernightRetrieveResult result = serviceWithNse(false).retrieve("USD_INR").results().get(0);

        assertEquals("UPSTOX", result.source());
        assertEquals("FRESH", result.status());
        org.junit.jupiter.api.Assertions.assertTrue(result.message().contains("NSE quote was unavailable"));
        verify(upstox).getOvernightQuote(any(Instrument.class));
    }

    @Test
    void usesAndPersistsNseGoldFuturesWithoutCallingUpstox() {
        Quote nseQuote = new Quote("GOLD", "NSE_COM|GOLD|29-Oct-2026",
                new BigDecimal("71000"), new BigDecimal("70900"), new BigDecimal("71100"),
                new BigDecimal("70800"), new BigDecimal("70975"), new BigDecimal("25"),
                new BigDecimal("0.035"), new BigDecimal("1200"), TEST_NOW.minusSeconds(5));
        when(nseMarketData.overnightNseQuote("GOLD")).thenReturn(nseQuote);

        OvernightRetrieveResult result = serviceWithNse(false).retrieve("GOLD").results().get(0);

        assertEquals("NSE", result.source());
        assertEquals("NSE", result.provider());
        assertEquals("NSE_COM|GOLD|29-Oct-2026", result.instrumentKey());
        assertEquals("FRESH", result.status());
        ArgumentCaptor<OvernightMarketDataEntity> saved =
                ArgumentCaptor.forClass(OvernightMarketDataEntity.class);
        verify(repository).save(saved.capture());
        assertEquals("NSE", saved.getValue().provider);
        verify(upstox, never()).getOvernightQuote(any(Instrument.class));
    }

    @Test
    void reportsGoldUnavailableInsteadOfFallingBackToUpstox() {
        when(nseMarketData.overnightNseQuote("GOLD"))
                .thenThrow(new IllegalStateException("No active Gold futures contract"));

        OvernightRetrieveResult result = serviceWithNse(false).retrieve("GOLD").results().get(0);

        assertEquals("NSE", result.source());
        assertEquals("NSE", result.provider());
        assertEquals("DATA_NOT_AVAILABLE", result.status());
        assertEquals("UNAVAILABLE", result.lifecycleStatus().name());
        org.junit.jupiter.api.Assertions.assertTrue(result.message().contains("No active Gold futures contract"));
        verify(upstox, never()).getOvernightQuote(any(Instrument.class));
        verify(repository, never()).save(any(OvernightMarketDataEntity.class));
    }

    @Test
    void smallProviderTimestampClockSkewDoesNotMarkFreshQuoteStale() {
        Quote providerQuote = quote(INSTRUMENTS.giftNifty(), TEST_NOW.plusMillis(500));
        when(upstox.getOvernightQuote(any(Instrument.class))).thenReturn(providerQuote);

        OvernightRetrieveResult result = service(false).retrieve("GIFT_NIFTY").results().get(0);

        assertEquals("FRESH", result.status());
        ArgumentCaptor<OvernightMarketDataEntity> saved =
                ArgumentCaptor.forClass(OvernightMarketDataEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(OvernightMarketDataEntity.Status.FRESH, saved.getValue().status);
        assertEquals(providerQuote.timestamp(), saved.getValue().providerTimestamp);
    }

    @Test
    void providerTimestampBeyondAllowedClockSkewIsStale() {
        Quote providerQuote = quote(INSTRUMENTS.giftNifty(), TEST_NOW.plusSeconds(6));
        when(upstox.getOvernightQuote(any(Instrument.class))).thenReturn(providerQuote);

        OvernightRetrieveResult result = service(false).retrieve("GIFT_NIFTY").results().get(0);

        assertEquals("STALE", result.status());
    }

    @Test
    void currencyAndCrudeRetrievalUseResolvedListedContracts() {
        when(upstox.getOvernightQuote(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument instrument = invocation.getArgument(0);
            return quote(instrument, TEST_NOW);
        });

        OvernightRetrieveResult currency = service(false).retrieve("USD_INR").results().get(0);
        OvernightRetrieveResult crude = service(false).retrieve("CRUDE_OIL").results().get(0);
        OvernightRetrieveResult gold = service(false).retrieve("GOLD").results().get(0);

        assertEquals("NCD_FO|USDINR", currency.instrumentKey());
        assertEquals("NSE_COM|CRUDEOIL", crude.instrumentKey());
        assertEquals("DATA_NOT_AVAILABLE", gold.status());
        assertEquals("UNAVAILABLE", gold.lifecycleStatus().name());
        ArgumentCaptor<OvernightMarketDataEntity> saved =
                ArgumentCaptor.forClass(OvernightMarketDataEntity.class);
        verify(repository, times(2)).save(saved.capture());
        assertEquals("NCD_FO", saved.getAllValues().get(0).segment);
        assertEquals("NSE_COM", saved.getAllValues().get(1).segment);
    }

    @Test
    void persistsBrentIndicatorLtpAsStaleWhenProviderOmitsSourceTimestamp() {
        Quote ltpOnly = new Quote("BRENT", "GLOBAL_INDICATOR|BZUSD",
                new BigDecimal("78.45"), null, null, null, null, null, null, null, null);
        when(upstox.getOvernightQuote(any(Instrument.class))).thenReturn(ltpOnly);

        OvernightRetrieveResult result = service(false).retrieve("BRENT").results().get(0);

        assertEquals("STALE", result.status());
        assertEquals("UPSTOX", result.source());
        assertEquals(new BigDecimal("78.45"), result.lastPrice());
        assertNull(result.dataTimestamp());
        org.junit.jupiter.api.Assertions.assertTrue(result.message().contains("freshness cannot be verified"));
        ArgumentCaptor<OvernightMarketDataEntity> saved =
                ArgumentCaptor.forClass(OvernightMarketDataEntity.class);
        verify(repository).save(saved.capture());
        assertNull(saved.getValue().providerTimestamp);
        assertEquals(new BigDecimal("78.45"), saved.getValue().lastPrice);
        assertEquals(OvernightMarketDataEntity.Status.STALE, saved.getValue().status);
    }

    @Test
    void doesNotUseUnverifiedUpstoxGoldWhenNseIsNotAvailable() {
        Quote ltpOnly = new Quote("GOLD", "NSE_COM|GOLD",
                new BigDecimal("71450.00"), null, null, null, null, null, null, null, null);
        when(upstox.getOvernightQuote(any(Instrument.class))).thenReturn(ltpOnly);

        OvernightRetrieveResult result = service(false).retrieve("GOLD").results().get(0);

        assertEquals("DATA_NOT_AVAILABLE", result.status());
        assertEquals("UNAVAILABLE", result.lifecycleStatus().name());
        assertNull(result.lastPrice());
        assertNull(result.netChange());
        assertNull(result.dataTimestamp());
        org.junit.jupiter.api.Assertions.assertTrue(result.message().contains("NSE Gold futures are not configured"));
        verify(upstox, never()).getOvernightQuote(any(Instrument.class));
        verify(repository, never()).save(any(OvernightMarketDataEntity.class));
    }

    @Test
    void retriesNextListedCurrencyContractWhenNearestHasNoUsableQuote() {
        List<ResolvedOvernightInstrument> contracts = List.of(
                new ResolvedOvernightInstrument("NCD_FO|near", "NSE", "NCD_FO", "USDINR FUT NEAR"),
                new ResolvedOvernightInstrument("NCD_FO|next", "NSE", "NCD_FO", "USDINR FUT NEXT"));
        when(instrumentResolver.resolve("USD_INR", "", "GLOBAL_INDICATOR")).thenReturn(contracts);
        when(upstox.getOvernightQuote(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument instrument = invocation.getArgument(0);
            if (instrument.instrumentKey().equals("NCD_FO|near")) {
                throw new NoUsableMarketQuoteException("no valid last price");
            }
            return quote(instrument, TEST_NOW);
        });

        OvernightRetrieveResult result = service(false).retrieve("USD_INR").results().get(0);

        assertEquals("NCD_FO|next", result.instrumentKey());
        assertEquals("FRESH", result.status());
        assertEquals("Earlier contracts had no usable quote; using NSE USDINR FUT NEXT.", result.message());
        verify(upstox, times(2)).getOvernightQuote(any(Instrument.class));
    }

    @Test
    void staleDatabaseRecordTriggersNewAppendOnlySnapshot() {
        OvernightMarketDataEntity stored = stored(TEST_NOW.minusSeconds(600));
        when(repository.findFirstByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
                INSTRUMENTS.giftNifty())).thenReturn(Optional.of(stored));
        when(upstox.getOvernightQuote(any(Instrument.class)))
                .thenReturn(quote(INSTRUMENTS.giftNifty(), TEST_NOW));

        OvernightRetrieveResult result = service(false).retrieve("GIFT_NIFTY").results().get(0);

        ArgumentCaptor<OvernightMarketDataEntity> saved =
                ArgumentCaptor.forClass(OvernightMarketDataEntity.class);
        verify(repository).save(saved.capture());
        assertEquals("UPSTOX", result.source());
        assertEquals("FRESH", result.status());
        assertNotSame(stored, saved.getValue());
        assertEquals(new BigDecimal("100.00"), stored.lastPrice);
    }

    @Test
    void failedProviderReturnsStaleDatabaseRecordOnlyWhenFallbackEnabled() {
        OvernightMarketDataEntity stored = stored(TEST_NOW.minusSeconds(600));
        when(repository.findFirstByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
                INSTRUMENTS.giftNifty())).thenReturn(Optional.of(stored));
        when(upstox.getOvernightQuote(any(Instrument.class)))
                .thenThrow(new MarketDataUnavailableException("provider unavailable"));

        OvernightRetrieveResult result = service(true).retrieve("GIFT_NIFTY").results().get(0);

        assertEquals("DATABASE", result.source());
        assertEquals("STALE", result.status());
        assertEquals(stored.providerTimestamp, result.dataTimestamp());
        verify(repository, times(1)).save(any(OvernightMarketDataEntity.class));
    }

    @Test
    void failedProviderWithoutDatabaseRecordReturnsStructuredProviderError() {
        when(upstox.getOvernightQuote(any(Instrument.class)))
                .thenThrow(new MarketDataUnavailableException("provider unavailable"));

        OvernightRetrieveResult result = service(false).retrieve("GIFT_NIFTY").results().get(0);

        assertEquals("PROVIDER_ERROR", result.status());
        assertEquals("UPSTOX", result.source());
        assertEquals("provider unavailable", result.message());
        assertNull(result.lastPrice());
        verify(repository).save(any(OvernightMarketDataEntity.class));
    }

    @Test
    void invalidProviderResponseIsNotPublishedAsMarketData() {
        when(upstox.getOvernightQuote(any(Instrument.class)))
                .thenReturn(new Quote("GIFT_NIFTY", INSTRUMENTS.giftNifty(),
                        BigDecimal.ZERO, null, null, null, BigDecimal.ZERO,
                        BigDecimal.ZERO, null, null, null));

        OvernightRetrieveResult result = service(false).retrieve("GIFT_NIFTY").results().get(0);

        assertEquals("DATA_NOT_AVAILABLE", result.status());
        assertEquals("UPSTOX", result.source());
        assertNull(result.lastPrice());
        ArgumentCaptor<OvernightMarketDataEntity> saved =
                ArgumentCaptor.forClass(OvernightMarketDataEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(OvernightMarketDataEntity.Status.DATA_NOT_AVAILABLE, saved.getValue().status);
        assertNull(saved.getValue().lastPrice);
    }

    @Test
    void successfulSnapshotsKeepProviderAndBackendTimestampsSeparate() {
        Instant providerTimestamp = TEST_NOW.minusSeconds(5);
        when(upstox.getOvernightQuote(any(Instrument.class)))
                .thenReturn(quote(INSTRUMENTS.giftNifty(), providerTimestamp));

        service(false).retrieve("GIFT_NIFTY");

        ArgumentCaptor<OvernightMarketDataEntity> saved =
                ArgumentCaptor.forClass(OvernightMarketDataEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(providerTimestamp, saved.getValue().providerTimestamp);
        assertNotEquals(saved.getValue().providerTimestamp, saved.getValue().retrievedAt);
    }

    @Test
    void allInstrumentRetrievalContinuesAfterAnIndividualProviderFailure() {
        when(upstox.getOvernightQuote(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument instrument = invocation.getArgument(0);
            if (instrument.instrumentKey().equals(INSTRUMENTS.giftNifty())) {
                throw new MarketDataUnavailableException("one instrument failed");
            }
            return quote(instrument.instrumentKey(), instrument.symbol(), TEST_NOW);
        });

        OvernightRetrieveResponse response = service(false).retrieve(null);

        assertEquals(12, response.results().size());
        assertEquals("PROVIDER_ERROR", response.results().get(0).status());
        assertEquals("FRESH", response.results().get(9).status());
        verify(upstox, times(11)).getOvernightQuote(any(Instrument.class));
    }

    @Test
    void rejectsUnsupportedLogicalInstrument() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service(false).retrieve("UNKNOWN")
        );
    }

    private OvernightGlobalMarketService service(boolean allowStaleFallback) {
        return new OvernightGlobalMarketService(upstox, instrumentResolver, INSTRUMENTS,
                repository, 300, 900, 5,
                allowStaleFallback, Clock.fixed(TEST_NOW, ZoneOffset.UTC));
    }

    private OvernightGlobalMarketService serviceWithNse(boolean allowStaleFallback) {
        return new OvernightGlobalMarketService(upstox, instrumentResolver, INSTRUMENTS,
                repository, 300, 900, 5,
                allowStaleFallback, Clock.fixed(TEST_NOW, ZoneOffset.UTC), nseMarketData);
    }

    private static OvernightMarketDataEntity stored(Instant providerTimestamp) {
        OvernightMarketDataEntity entity = new OvernightMarketDataEntity();
        entity.id = 1L;
        entity.instrumentName = "GIFT_NIFTY";
        entity.instrumentKey = INSTRUMENTS.giftNifty();
        entity.provider = "UPSTOX";
        entity.segment = "GLOBAL_INDEX";
        entity.lastPrice = new BigDecimal("100.00");
        entity.closePrice = new BigDecimal("99.00");
        entity.netChange = BigDecimal.ONE;
        entity.providerTimestamp = providerTimestamp;
        entity.retrievedAt = providerTimestamp.plusSeconds(1);
        entity.status = OvernightMarketDataEntity.Status.FRESH;
        return entity;
    }

    private static Quote quote(String key, String symbol, Instant timestamp) {
        return new Quote(symbol, key,
                new BigDecimal("101.25"), new BigDecimal("99.00"), new BigDecimal("102.00"),
                new BigDecimal("98.00"), new BigDecimal("100.00"), new BigDecimal("1.25"),
                new BigDecimal("1.25"), new BigDecimal("500"), timestamp);
    }

    private static Quote quote(String key, Instant timestamp) {
        return quote(key, "GIFT_NIFTY", timestamp);
    }

    private static Quote quote(Instrument instrument, Instant timestamp) {
        return quote(instrument.instrumentKey(), instrument.symbol(), timestamp);
    }
}
