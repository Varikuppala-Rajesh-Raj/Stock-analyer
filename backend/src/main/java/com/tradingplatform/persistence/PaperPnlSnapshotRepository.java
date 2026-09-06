package com.tradingplatform.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaperPnlSnapshotRepository
        extends JpaRepository<PaperPnlSnapshotEntity, Long> {

    List<PaperPnlSnapshotEntity>
    findTop500ByAccountIdOrderBySnapshotTimeAsc(UUID accountId);
}