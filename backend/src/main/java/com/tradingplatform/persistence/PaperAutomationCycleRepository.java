package com.tradingplatform.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperAutomationCycleRepository extends JpaRepository<PaperAutomationCycleEntity, UUID> {
    List<PaperAutomationCycleEntity> findTop100ByOrderByTimestampDesc();
}
