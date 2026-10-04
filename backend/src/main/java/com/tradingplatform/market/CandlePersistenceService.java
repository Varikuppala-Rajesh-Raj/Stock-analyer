package com.tradingplatform.market;

import com.tradingplatform.persistence.CandleEntity;
import com.tradingplatform.persistence.CandleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandlePersistenceService {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private final CandleRepository candles;

    public CandlePersistenceService(CandleRepository candles) {
        this.candles = candles;
    }

    @Transactional
    public void upsert(String key, Timeframe timeframe, List<Candle> values) {
        for (Candle candle : values) {
            CandleEntity entity = candles.findByInstrumentKeyAndTimeframeAndTimestamp(
                    key, timeframe.name(), candle.timestamp()).orElseGet(CandleEntity::new);
            entity.instrumentKey = key;
            entity.timeframe = timeframe.name();
            entity.timestamp = candle.timestamp();
            entity.open = candle.open();
            entity.high = candle.high();
            entity.low = candle.low();
            entity.close = candle.close();
            entity.volume = candle.volume();
            candles.save(entity);
        }
    }

    public List<Candle> find(String key, Timeframe timeframe, LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(INDIA).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(INDIA).toInstant().minusNanos(1);
        return candles.findByInstrumentKeyAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
                        key, timeframe.name(), start, end)
                .stream()
                .map(entity -> new Candle(entity.timestamp, entity.open, entity.high, entity.low,
                        entity.close, entity.volume))
                .toList();
    }
}
