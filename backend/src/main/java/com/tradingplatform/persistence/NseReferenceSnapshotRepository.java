package com.tradingplatform.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NseReferenceSnapshotRepository extends JpaRepository<NseReferenceSnapshotEntity, Long> {
    Optional<NseReferenceSnapshotEntity> findFirstByDatasetKeyOrderByRetrievedAtDesc(String datasetKey);
}
