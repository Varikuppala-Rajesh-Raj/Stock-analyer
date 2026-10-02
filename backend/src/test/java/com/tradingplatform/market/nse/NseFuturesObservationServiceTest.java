package com.tradingplatform.market.nse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.persistence.NseFuturesObservationEntity;
import com.tradingplatform.persistence.NseFuturesObservationRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NseFuturesObservationServiceTest {
    private final NseFuturesObservationRepository repository = mock(NseFuturesObservationRepository.class);
    private final NseFuturesObservationService service = new NseFuturesObservationService(repository);

    @Test
    void asOfLookupUsesOnlyObservationsAtOrBeforeTimestampAndHonorsFreshness() {
        Instant asOf = Instant.parse("2026-10-01T10:00:00Z");
        NseFuturesObservationEntity entity = entity(asOf.minusSeconds(30));
        when(repository.findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(asOf))
                .thenReturn(Optional.of(entity));

        var result = service.latestAtOrBefore(asOf, Duration.ofSeconds(90));

        assertTrue(result.isPresent());
        assertEquals("FUTIDXNIFTY27-10-2026XX0.00", result.get().identifier());
        assertEquals(25_000L, result.get().openInterest());
        assertTrue(service.latestAtOrBefore(asOf, Duration.ofSeconds(10)).isEmpty());
        verify(repository, org.mockito.Mockito.times(2))
                .findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(asOf);
    }

    @Test
    void rejectsRepositoryRowsThatAreAfterTheRequestedAsOfTime() {
        Instant asOf = Instant.parse("2026-10-01T10:00:00Z");
        when(repository.findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(asOf))
                .thenReturn(Optional.of(entity(asOf.plusSeconds(1))));

        assertTrue(service.latestAtOrBefore(asOf, Duration.ofMinutes(1)).isEmpty());
    }

        @Test
        void m5JoinUsesOnlyFreshSameContractSnapshotsAndComputesRealDeltas() {
        Instant asOf = Instant.parse("2026-10-01T10:00:00Z");
        NseFuturesObservationEntity current = entity(asOf.minusSeconds(30));
        current.cumulativeVolume = 1_200L;
        current.openInterest = 25_000L;
        current.changeInOpenInterest = 600L;
        current.lastPrice = BigDecimal.valueOf(25_100);
        NseFuturesObservationEntity previous = entity(asOf.minus(Duration.ofMinutes(5)).minusSeconds(30));
        previous.cumulativeVolume = 1_000L;
        previous.openInterest = 24_900L;
        when(repository.findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(asOf))
            .thenReturn(Optional.of(current));
        when(repository.findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(asOf.minus(Duration.ofMinutes(5))))
            .thenReturn(Optional.of(previous));

        var result = service.fiveMinuteFeaturesAt(asOf, Duration.ofSeconds(90));

        assertTrue(result.isPresent());
        assertEquals(200L, result.get().contractsTradedOverFiveMinutes());
        assertEquals(100L, result.get().openInterestChangeOverFiveMinutes());
        assertEquals(600L, result.get().sessionChangeInOpenInterest());
        assertEquals(current.timestamp, result.get().futuresSnapshotTimestamp());
        assertEquals(asOf, result.get().predictionTimestamp());
        }

        @Test
        void m5JoinRejectsContractRolloverBetweenEndpoints() {
        Instant asOf = Instant.parse("2026-10-01T10:00:00Z");
        NseFuturesObservationEntity current = entity(asOf.minusSeconds(30));
        NseFuturesObservationEntity previous = entity(asOf.minus(Duration.ofMinutes(5)).minusSeconds(30));
        previous.identifier = "FUTIDXNIFTY24-11-2026XX0.00";
        previous.expiry = "24-Nov-2026";
        when(repository.findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(asOf))
            .thenReturn(Optional.of(current));
        when(repository.findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(asOf.minus(Duration.ofMinutes(5))))
            .thenReturn(Optional.of(previous));

        assertTrue(service.fiveMinuteFeaturesAt(asOf, Duration.ofSeconds(90)).isEmpty());
        }

    @Test
    void persistsOrUpdatesTheSameContractTimestampWithoutCreatingDuplicates() {
        Instant timestamp = Instant.parse("2026-10-01T10:00:00Z");
        NseFuturesObservationEntity existing = entity(timestamp);
        when(repository.findByIdentifierAndTimestamp("FUTIDXNIFTY27-10-2026XX0.00", timestamp))
                .thenReturn(Optional.of(existing));
        NseFuturesObservation observation = new NseFuturesObservation(
                timestamp, "01-Oct-2026 15:30:00", "FUTIDXNIFTY27-10-2026XX0.00",
                "FUTIDX", "27-Oct-2026", 25_100.0, 25_000.0, 25_200.0, 24_950.0,
                24_900.0, 200.0, 0.8, 25_500L, 600L, 2.4, 12_000L, 30_000_000.0,
                "NIFTY", 25_050.0);

        service.persist(observation);

        verify(repository).save(existing);
        assertEquals(BigDecimal.valueOf(25_100.0), existing.lastPrice);
        assertEquals(25_500L, existing.openInterest);
        assertEquals(600L, existing.changeInOpenInterest);
        assertEquals(12_000L, existing.cumulativeVolume);
    }

    private NseFuturesObservationEntity entity(Instant timestamp) {
        NseFuturesObservationEntity entity = new NseFuturesObservationEntity();
        entity.timestamp = timestamp;
        entity.sourceTimestamp = "01-Oct-2026 15:29:30";
        entity.identifier = "FUTIDXNIFTY27-10-2026XX0.00";
        entity.instrumentType = "FUTIDX";
        entity.expiry = "27-Oct-2026";
        entity.lastPrice = BigDecimal.valueOf(25_000);
        entity.cumulativeVolume = 1_000L;
        entity.openInterest = 25_000L;
        entity.underlying = "NIFTY";
        entity.underlyingValue = BigDecimal.valueOf(24_950);
        return entity;
    }
}