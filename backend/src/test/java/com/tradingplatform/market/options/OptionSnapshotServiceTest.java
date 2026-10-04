package com.tradingplatform.market.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.persistence.OptionMarketSnapshotEntity;
import com.tradingplatform.persistence.OptionMarketSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OptionSnapshotServiceTest {
    @Mock private OptionMarketSnapshotRepository repository;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private OptionSnapshotService service;

    @Test
    void returnsSavedPremiumPointsForTheSelectedContractAndExpiry() {
        String instrumentKey = "NSE_FO|NIFTY26OCT23000PE";
        LocalDate expiry = LocalDate.of(2026, 10, 29);
        LocalDate from = LocalDate.of(2026, 10, 4);
        LocalDate to = LocalDate.of(2026, 10, 5);
        Instant timestamp = Instant.parse("2026-10-04T09:20:00Z");
        OptionMarketSnapshotEntity snapshot = new OptionMarketSnapshotEntity();
        snapshot.timestamp = timestamp;
        snapshot.expiry = expiry;
        snapshot.ltp = new BigDecimal("145.50");
        snapshot.volume = null;
        when(repository.findByInstrumentKeyAndTimestampBetweenOrderByTimestampAsc(
                instrumentKey,
                from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()))
                .thenReturn(List.of(snapshot));

        var points = service.history(instrumentKey, expiry.toString(), from, to);

        assertEquals(1, points.size());
        assertEquals(timestamp, points.get(0).timestamp());
        assertEquals(new BigDecimal("145.50"), points.get(0).price());
        assertEquals(BigDecimal.ZERO, points.get(0).volume());
        verify(repository).findByInstrumentKeyAndTimestampBetweenOrderByTimestampAsc(
                instrumentKey,
                from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
    }
}
