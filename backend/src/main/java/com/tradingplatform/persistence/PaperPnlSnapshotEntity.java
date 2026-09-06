package com.tradingplatform.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "paper_pnl_snapshots",
    indexes = {
        @Index(
            name = "idx_paper_pnl_account_time",
            columnList = "account_id, snapshot_time DESC"
        )
    }
)
public class PaperPnlSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private java.util.UUID accountId;

    @Column(name = "snapshot_time", nullable = false)
    private Instant snapshotTime;

    @Column(name = "realized_pnl", nullable = false, precision = 20, scale = 6)
    private BigDecimal realizedPnl;

    @Column(name = "unrealized_pnl", nullable = false, precision = 20, scale = 6)
    private BigDecimal unrealizedPnl;

    @Column(name = "total_pnl", nullable = false, precision = 20, scale = 6)
    private BigDecimal totalPnl;

    @Column(name = "equity", nullable = false, precision = 20, scale = 6)
    private BigDecimal equity;

    protected PaperPnlSnapshotEntity() {
    }

    public PaperPnlSnapshotEntity(
            java.util.UUID accountId,
            Instant snapshotTime,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal totalPnl,
            BigDecimal equity
    ) {
        this.accountId = accountId;
        this.snapshotTime = snapshotTime;
        this.realizedPnl = realizedPnl;
        this.unrealizedPnl = unrealizedPnl;
        this.totalPnl = totalPnl;
        this.equity = equity;
    }

    public Long getId() {
        return id;
    }

    public java.util.UUID getAccountId() {
        return accountId;
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public BigDecimal getTotalPnl() {
        return totalPnl;
    }

    public BigDecimal getEquity() {
        return equity;
    }
}