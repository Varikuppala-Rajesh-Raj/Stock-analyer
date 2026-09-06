package com.tradingplatform.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    Optional<CandleEntity> findByInstrumentKeyAndTimeframeAndTimestamp(
            String instrumentKey,
            String timeframe,
            Instant timestamp
    );

    List<CandleEntity> findByInstrumentKeyAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            String instrumentKey,
            String timeframe,
            Instant from,
            Instant to
    );

    List<CandleEntity> findByInstrumentKeyAndTimeframeAndTimestampLessThanEqualOrderByTimestampAsc(
            String instrumentKey,
            String timeframe,
            Instant timestamp
    );
}