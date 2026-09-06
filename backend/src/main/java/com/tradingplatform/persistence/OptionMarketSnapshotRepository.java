package com.tradingplatform.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionMarketSnapshotRepository extends JpaRepository<OptionMarketSnapshotEntity, Long> {
    Optional<OptionMarketSnapshotEntity> findByTimestampAndInstrumentKey(Instant timestamp, String instrumentKey);
    List<OptionMarketSnapshotEntity> findByInstrumentKeyAndTimestampBetweenOrderByTimestampAsc(String instrumentKey, Instant from, Instant to);
    List<OptionMarketSnapshotEntity> findBySymbolOrderByTimestampAsc(String symbol);
    List<OptionMarketSnapshotEntity> findBySymbolAndExpiryOrderByTimestampDesc(String symbol, java.time.LocalDate expiry);
}
