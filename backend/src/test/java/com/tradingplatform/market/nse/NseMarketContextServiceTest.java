package com.tradingplatform.market.nse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class NseMarketContextServiceTest {
    private static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter SPOT_TIME = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter FUTURES_TIME = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void startsCollectingAndDoesNotInventObservations() {
        NseMarketContextService service = service(mock(NseMarketDataService.class));

        var snapshot = service.snapshot();

        assertEquals(NseMarketContextSnapshot.Status.COLLECTING, snapshot.nifty().return1m().status());
        assertEquals(NseMarketContextSnapshot.Status.COLLECTING, snapshot.nifty().return15m().status());
        assertNull(snapshot.nifty().return1m().value());
        assertNull(snapshot.futures().ltp());
        assertEquals(0, service.spotObservationCount());
        assertEquals(0, service.futuresObservationCount());
    }

    @Test
    void calculatesTimestampBasedReturnsAndFiveMinuteFuturesLead() {
        NseMarketDataService nse = mock(NseMarketDataService.class);
        NseMarketContextService service = service(nse);
        Instant t0 = testNow().minusSeconds(16 * 60L).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant t1 = t0.plusSeconds(10 * 60L);
        Instant t2 = t0.plusSeconds(16 * 60L);

        stubObservations(nse,
            spotPayload(100, t0), spotPayload(110, t1), spotPayload(120, t2),
            futuresPayload(200, 100, "FUTIDXNIFTY27-10-2026XX0.00", t0),
            futuresPayload(210, 110, "FUTIDXNIFTY27-10-2026XX0.00", t1),
            futuresPayload(240, 120, "FUTIDXNIFTY27-10-2026XX0.00", t2));
        service.collectNow();
        service.collectNow();
        service.collectNow();

        var snapshot = service.snapshot();
        assertEquals(20.0, snapshot.nifty().return15m().value(), 0.0001);
        assertEquals(10.0 / 110.0 * 100.0, snapshot.nifty().return5m().value(), 0.0001);
        assertEquals(20.0, snapshot.futures().return15m().value(), 0.0001);
        assertEquals(30.0 / 210.0 * 100.0, snapshot.futures().return5m().value(), 0.0001);
        assertEquals(120.0, snapshot.futures().basis());
        assertEquals(snapshot.futures().return5m().value() - snapshot.nifty().return5m().value(),
                snapshot.futuresLead().value(), 0.0001);
        assertEquals(NseMarketContextSnapshot.Status.COLLECTED, snapshot.futuresLead().status());
    }

    @Test
    void persistsTimestampedFuturesPriceVolumeOiAndContractIdentity() {
        NseMarketDataService nse = mock(NseMarketDataService.class);
        NseFuturesObservationService persisted = mock(NseFuturesObservationService.class);
        NseMarketContextService service = service(nse, persisted);
        Instant timestamp = testNow().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        when(nse.indexData("All")).thenReturn(spotPayload(100, timestamp));
        when(nse.futures()).thenReturn(futuresPayload(200, 100,
                "FUTIDXNIFTY27-10-2026XX0.00", timestamp));

        service.collectNow();

        org.mockito.ArgumentCaptor<NseFuturesObservation> captor =
                org.mockito.ArgumentCaptor.forClass(NseFuturesObservation.class);
        verify(persisted).persist(captor.capture());
        NseFuturesObservation observation = captor.getValue();
        assertEquals(timestamp, observation.timestamp());
        assertEquals("FUTIDXNIFTY27-10-2026XX0.00", observation.identifier());
        assertEquals("27-Oct-2026", observation.expiry());
        assertEquals(200.0, observation.price());
        assertEquals(10_000L, observation.openInterest());
        assertEquals(1L, observation.changeInOpenInterest());
        assertEquals(100L, observation.volume());
    }

    @Test
    void parsesStringAndCommaFormattedSpotAndFuturesNumbers() {
        NseMarketDataService nse = mock(NseMarketDataService.class);
        NseMarketContextService service = service(nse);
        Instant t1 = testNow().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        var spotRow = new java.util.LinkedHashMap<String, Object>();
        spotRow.put("indexName", "NIFTY 50");
        spotRow.put("timeVal", SPOT_TIME.format(t1.atZone(NSE_ZONE)));
        spotRow.put("last", "25,123.45");
        spotRow.put("open", "25,000.00");
        spotRow.put("previousClose", "24,900.00");
        var futuresRow = new java.util.LinkedHashMap<String, Object>();
        futuresRow.put("identifier", "FUTIDXNIFTY27-10-2026XX0.00");
        futuresRow.put("instrumentType", "FUTIDX");
        futuresRow.put("expiryDate", "27-Oct-2026");
        futuresRow.put("lastPrice", "25,200.50");
        futuresRow.put("underlyingValue", "25,123.45");
        futuresRow.put("openInterest", "1,234,567");
        futuresRow.put("changeinOpenInterest", "1,234");
        futuresRow.put("totalTradedVolume", "12,345");
        futuresRow.put("change", "25,12.34");
        futuresRow.put("underlying", "NIFTY");
        when(nse.indexData("All")).thenReturn(
                mapper.valueToTree(java.util.Map.of("data", java.util.List.of(spotRow))));
        when(nse.futures()).thenReturn(mapper.valueToTree(java.util.Map.of(
                "timestamp", FUTURES_TIME.format(t1.atZone(NSE_ZONE)),
                "data", java.util.List.of(futuresRow))));

        service.collectNow();

        var snapshot = service.snapshot();
        assertEquals(25_123.45, snapshot.nifty().ltp());
        assertEquals(25_200.50, snapshot.futures().ltp());
        assertEquals(1_234_567L, snapshot.futures().openInterest());
        assertEquals(12_345L, snapshot.futures().volume());
        assertNull(snapshot.futures().change());
    }

    @Test
    void previousNseSessionRemainsCollectedInMorningAndItsFinalReturnsRemainUsable() {
        NseMarketDataService nse = mock(NseMarketDataService.class);
        Instant morning = LocalDate.of(2026, 10, 9).atTime(8, 0).atZone(NSE_ZONE).toInstant();
        Instant prior5m = LocalDate.of(2026, 10, 8).atTime(15, 20).atZone(NSE_ZONE).toInstant();
        Instant priorClose = LocalDate.of(2026, 10, 8).atTime(15, 25).atZone(NSE_ZONE).toInstant();
        NseMarketContextService service = new NseMarketContextService(nse,
                mock(NseFuturesObservationService.class), 180_000, 90_000, 2_000,
                Clock.fixed(morning, ZoneId.of("UTC")));
        when(nse.indexData("All")).thenReturn(spotPayload(100, prior5m), spotPayload(105, priorClose));
        when(nse.futures()).thenReturn(
                futuresPayload(200, 100, "FUTIDXNIFTY27-10-2026XX0.00", prior5m),
                futuresPayload(210, 105, "FUTIDXNIFTY27-10-2026XX0.00", priorClose));

        service.collectNow();
        service.collectNow();
        var snapshot = service.snapshot();

        assertEquals(NseMarketContextSnapshot.Status.COLLECTED, snapshot.nifty().status());
        assertEquals(NseMarketContextSnapshot.Status.COLLECTED, snapshot.futures().status());
        assertEquals(NseMarketContextSnapshot.Status.COLLECTED, snapshot.nifty().return5m().status());
        assertEquals(5.0, snapshot.nifty().return5m().value(), 0.0001);
        assertTrue(snapshot.nifty().timestamp().contains("08-Oct-2026"));
    }

    @Test
    void priorNseSessionCannotSatisfyTheNextTradingSessionAfterTheCycleStarts() {
        NseMarketDataService nse = mock(NseMarketDataService.class);
        Instant cycle = LocalDate.of(2026, 10, 8).atTime(15, 31).atZone(NSE_ZONE).toInstant();
        Instant olderSession = LocalDate.of(2026, 10, 7).atTime(15, 20).atZone(NSE_ZONE).toInstant();
        NseMarketContextService service = new NseMarketContextService(nse,
                mock(NseFuturesObservationService.class), 180_000, 90_000, 2_000,
                Clock.fixed(cycle, ZoneId.of("UTC")));
        when(nse.indexData("All")).thenReturn(spotPayload(100, olderSession));

        service.collectNow();

        assertEquals(NseMarketContextSnapshot.Status.STALE, service.snapshot().nifty().status());
    }

    @Test
    void skipsStaleAndFutureDatedFuturesObservationsWhenPersisting() {
        for (Instant timestamp : java.util.List.of(
                testNow().minusSeconds(6 * 60L),
                testNow().plusSeconds(60))) {
            NseMarketDataService nse = mock(NseMarketDataService.class);
            NseFuturesObservationService persisted = mock(NseFuturesObservationService.class);
            NseMarketContextService service = service(nse, persisted);
            when(nse.futures()).thenReturn(futuresPayload(
                    200, 100, "FUTIDXNIFTY27-10-2026XX0.00", timestamp));

            service.collectNow();

            verify(persisted, never()).persist(any());
        }
    }

    @Test
    void calculatesTenMinuteReturnsOnlyWhenARealNearTargetObservationExists() {
        NseMarketDataService nse = mock(NseMarketDataService.class);
        NseMarketContextService service = service(nse);
        Instant t0 = testNow().minusSeconds(11 * 60L).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Instant t1 = t0.plusSeconds(60);
        Instant t2 = t0.plusSeconds(11 * 60L);
        stubObservations(nse,
                spotPayload(100, t0), spotPayload(102, t1), spotPayload(110, t2),
                futuresPayload(200, 100, "FUTIDXNIFTY27-10-2026XX0.00", t0),
                futuresPayload(204, 102, "FUTIDXNIFTY27-10-2026XX0.00", t1),
                futuresPayload(220, 110, "FUTIDXNIFTY27-10-2026XX0.00", t2));
        service.collectNow();
        service.collectNow();
        service.collectNow();

        var snapshot = service.snapshot();
        assertEquals((110.0 - 102.0) / 102.0 * 100.0, snapshot.nifty().return10m().value(), 0.0001);
        assertEquals((220.0 - 204.0) / 204.0 * 100.0, snapshot.futures().return10m().value(), 0.0001);
    }

    @Test
    void doesNotUsePreviousSessionDataForTodaysOpeningWindow() {
        NseMarketDataService nse = mock(NseMarketDataService.class);
        NseMarketContextService service = service(nse);
        Instant todayOpen = testNow().minusSeconds(60);
        Instant previousClose = todayOpen.minusSeconds(24 * 60 * 60L).minusSeconds(30 * 60L);

        when(nse.indexData("All")).thenReturn(
                spotPayload(100, previousClose),
                spotPayload(102, todayOpen));
        when(nse.futures()).thenReturn(
                futuresPayload(200, 100, "FUTIDXNIFTY27-10-2026XX0.00", previousClose),
                futuresPayload(204, 102, "FUTIDXNIFTY27-10-2026XX0.00", todayOpen));
        service.collectNow();
        service.collectNow();

        var snapshot = service.snapshot();
        assertEquals(NseMarketContextSnapshot.Status.COLLECTING, snapshot.nifty().return1m().status());
        assertEquals(NseMarketContextSnapshot.Status.COLLECTING, snapshot.nifty().return15m().status());
        assertNull(snapshot.nifty().return15m().value());
        assertEquals(NseMarketContextSnapshot.Status.COLLECTING, snapshot.futures().return5m().status());
    }

    @Test
    void classifiesStaleAndFailedSourcesWithoutZeroValues() {
        NseMarketDataService staleNse = mock(NseMarketDataService.class);
        NseMarketContextService staleService = service(staleNse);
        Instant old = testNow().minusSeconds(24 * 60 * 60L);
        stubObservations(staleNse,
            spotPayload(100, old), spotPayload(100, old), spotPayload(100, old),
            futuresPayload(200, 100, "FUTIDXNIFTY27-10-2026XX0.00", old),
            futuresPayload(200, 100, "FUTIDXNIFTY27-10-2026XX0.00", old),
            futuresPayload(200, 100, "FUTIDXNIFTY27-10-2026XX0.00", old));
        staleService.collectNow();
        assertEquals(NseMarketContextSnapshot.Status.STALE, staleService.snapshot().nifty().status());
        assertEquals(NseMarketContextSnapshot.Status.STALE, staleService.snapshot().futures().return5m().status());

        NseMarketDataService failedNse = mock(NseMarketDataService.class);
        when(failedNse.indexData("All")).thenThrow(new IllegalStateException("NSE unavailable"));
        when(failedNse.futures()).thenThrow(new IllegalStateException("NSE unavailable"));
        NseMarketContextService failedService = service(failedNse);
        failedService.collectNow();
        var failed = failedService.snapshot();
        assertEquals(NseMarketContextSnapshot.Status.UNAVAILABLE, failed.nifty().status());
        assertEquals(NseMarketContextSnapshot.Status.UNAVAILABLE, failed.futures().status());
        assertNull(failed.futures().ltp());
        assertNull(failed.futuresLead().value());
    }

    @Test
    void rolloverClearsOnlyFuturesHistoryAndNewBackendInstanceStartsEmpty() {
        NseMarketDataService nse = mock(NseMarketDataService.class);
        NseMarketContextService service = service(nse);
        Instant t0 = testNow().minusSeconds(10 * 60L).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Instant t1 = t0.plusSeconds(5 * 60L);
        Instant t2 = testNow().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        stubObservations(nse,
            spotPayload(100, t0), spotPayload(101, t1), spotPayload(102, t2),
            futuresPayload(200, 100, "FUTIDXNIFTY27-10-2026XX0.00", t0),
            futuresPayload(201, 101, "FUTIDXNIFTY27-10-2026XX0.00", t1),
            futuresPayload(202, 102, "FUTIDXNIFTY24-11-2026XX0.00", t2));
        service.collectNow();
        service.collectNow();
        assertEquals(2, service.futuresObservationCount());
        service.collectNow();

        assertEquals(1, service.futuresObservationCount());
        assertEquals(3, service.spotObservationCount());
        assertEquals(NseMarketContextSnapshot.Status.COLLECTING, service.snapshot().futures().return5m().status());
        assertEquals(0, service(mock(NseMarketDataService.class)).futuresObservationCount());
    }

    private NseMarketContextService service(NseMarketDataService nse) {
        return service(nse, mock(NseFuturesObservationService.class));
    }

    private NseMarketContextService service(NseMarketDataService nse,
                                            NseFuturesObservationService futuresObservations) {
        return new NseMarketContextService(nse, futuresObservations, 180_000, 90_000, 100,
                Clock.fixed(testNow(), ZoneId.of("UTC")));
    }

    private static Instant testNow() {
        LocalDate date = LocalDate.now(NSE_ZONE);
        while (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        return date.atTime(15, 40).atZone(NSE_ZONE).toInstant();
    }

    private void stubObservations(NseMarketDataService nse, JsonNode spot0, JsonNode spot1, JsonNode spot2,
                                  JsonNode futures0, JsonNode futures1, JsonNode futures2) {
        when(nse.indexData("All")).thenReturn(spot0, spot1, spot2);
        when(nse.futures()).thenReturn(futures0, futures1, futures2);
    }

    private JsonNode spotPayload(double price, Instant timestamp) {
        String time = SPOT_TIME.format(timestamp.atZone(NSE_ZONE));
        return mapper.valueToTree(java.util.Map.of("data", java.util.List.of(java.util.Map.of(
                "indexName", "NIFTY 50", "timeVal", time, "last", price,
                "open", price, "high", price, "low", price,
                "previousClose", price - 1, "percChange", 0.1))));
    }

    private JsonNode futuresPayload(double price, double underlying, String identifier, Instant timestamp) {
        String time = FUTURES_TIME.format(timestamp.atZone(NSE_ZONE));
        var row = new java.util.LinkedHashMap<String, Object>();
        row.put("identifier", identifier);
        row.put("instrumentType", "FUTIDX");
        row.put("expiryDate", identifier.contains("24-11") ? "24-Nov-2026" : "27-Oct-2026");
        row.put("lastPrice", price);
        row.put("openPrice", price);
        row.put("highPrice", price);
        row.put("lowPrice", price);
        row.put("prevClose", price - 1);
        row.put("change", 1.0);
        row.put("pchange", 0.1);
        row.put("openInterest", 10_000L);
        row.put("changeinOpenInterest", 1L);
        row.put("pchangeinOpenInterest", 0.1);
        row.put("totalTradedVolume", 100L);
        row.put("totalTurnover", 1000.0);
        row.put("underlying", "NIFTY");
        row.put("underlyingValue", underlying);
        return mapper.valueToTree(java.util.Map.of("timestamp", time, "data", java.util.List.of(row)));
    }
}