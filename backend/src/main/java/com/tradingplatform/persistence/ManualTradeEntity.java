package com.tradingplatform.persistence;

import com.tradingplatform.paper.ManualTradeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "manual_trades")
public class ManualTradeEntity {
    @Id
    public UUID id;

    @Column(name = "instrument_type", nullable = false)
    public String instrumentType;

    @Column(name = "instrument_key", nullable = false)
    public String instrumentKey;

    @Column(nullable = false)
    public String symbol;

    @Column(nullable = false)
    public String exchange;

    public LocalDate expiry;

    @Column(name = "option_type")
    public String optionType;

    public BigDecimal strike;

    @Column(nullable = false)
    public int quantity;

    @Column(name = "entry_price", nullable = false)
    public BigDecimal entryPrice;

    @Column(name = "current_price", nullable = false)
    public BigDecimal currentPrice;

    @Column(name = "exit_price")
    public BigDecimal exitPrice;

    @Column(name = "entry_charges", nullable = false)
    public BigDecimal entryCharges;

    @Column(name = "exit_charges", nullable = false)
    public BigDecimal exitCharges;

    @Column(name = "unrealized_pnl", nullable = false)
    public BigDecimal unrealizedPnl;

    @Column(name = "realized_pnl", nullable = false)
    public BigDecimal realizedPnl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ManualTradeStatus status;

    @Column(name = "opened_at", nullable = false)
    public Instant openedAt;

    @Column(name = "closed_at")
    public Instant closedAt;

    @Column(name = "price_updated_at", nullable = false)
    public Instant priceUpdatedAt;
}
