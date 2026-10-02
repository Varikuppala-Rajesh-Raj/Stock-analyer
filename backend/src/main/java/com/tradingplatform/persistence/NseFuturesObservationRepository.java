package com.tradingplatform.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NseFuturesObservationRepository extends JpaRepository<NseFuturesObservationEntity, Long> {
    Optional<NseFuturesObservationEntity> findByIdentifierAndTimestamp(String identifier, Instant timestamp);

    Optional<NseFuturesObservationEntity> findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(Instant timestamp);

    List<NseFuturesObservationEntity> findByTimestampBetweenOrderByTimestampAscIdAsc(Instant from, Instant to);
}