package com.tradingplatform.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OvernightMarketDataRepository extends JpaRepository<OvernightMarketDataEntity, Long> {
    Optional<OvernightMarketDataEntity> findFirstByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
            String instrumentKey);

    Optional<OvernightMarketDataEntity> findFirstByInstrumentNameAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
            String instrumentName);

    Optional<OvernightMarketDataEntity> findFirstByInstrumentNameAndLastPriceIsNotNullOrderByRetrievedAtDescIdDesc(
            String instrumentName);

    Optional<OvernightMarketDataEntity> findFirstByInstrumentNameOrderByRetrievedAtDescIdDesc(String instrumentName);

    List<OvernightMarketDataEntity> findTop100ByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
            String instrumentKey);
}
