package com.tradingplatform.persistence;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface PredictionRepository extends JpaRepository<PredictionEntity, UUID> { List<PredictionEntity> findTop100BySymbolOrderByTimestampDesc(String symbol); List<PredictionEntity> findBySymbolAndEvaluatedAtIsNotNullOrderByEvaluatedAtDesc(String symbol); }
