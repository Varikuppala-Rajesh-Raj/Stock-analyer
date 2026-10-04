package com.tradingplatform.market;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.persistence.CandleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandlePersistenceServiceTest {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Test
    void queriesLocalDateUsingIstDayBoundaries() {
        CandleRepository repository = mock(CandleRepository.class);
        CandlePersistenceService service = new CandlePersistenceService(repository);
        LocalDate date = LocalDate.of(2026, 10, 1);
        Instant start = date.atStartOfDay(INDIA).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(INDIA).toInstant().minusNanos(1);
        when(repository.findByInstrumentKeyAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
                "NSE_INDEX|Nifty 50", "M5", start, end)).thenReturn(List.of());

        service.find("NSE_INDEX|Nifty 50", Timeframe.M5, date, date);

        verify(repository).findByInstrumentKeyAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
                "NSE_INDEX|Nifty 50", "M5", start, end);
    }
}
