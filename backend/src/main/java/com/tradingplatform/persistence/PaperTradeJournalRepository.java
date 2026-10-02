package com.tradingplatform.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaperTradeJournalRepository extends JpaRepository<PaperTradeJournalEntity, UUID> {
    List<PaperTradeJournalEntity> findByAccountIdOrderByTimestampDesc(UUID accountId);
    List<PaperTradeJournalEntity> findByTradeTypeOrderByTimestampDesc(String tradeType);
    List<PaperTradeJournalEntity> findByTradeTypeAndAccountIdOrderByTimestampDesc(String tradeType, UUID accountId);
}
