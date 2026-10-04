package com.tradingplatform.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualTradeRepository extends JpaRepository<ManualTradeEntity, UUID> {
    List<ManualTradeEntity> findAllByOrderByOpenedAtDesc();
}
