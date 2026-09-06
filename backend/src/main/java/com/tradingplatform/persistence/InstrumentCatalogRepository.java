package com.tradingplatform.persistence;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface InstrumentCatalogRepository extends JpaRepository<InstrumentEntity,String> { List<InstrumentEntity> findBySymbolIgnoreCaseAndActiveTrueOrderByExchangeAscSegmentAsc(String symbol); List<InstrumentEntity> findByActiveTrueOrderBySymbolAsc(); }
