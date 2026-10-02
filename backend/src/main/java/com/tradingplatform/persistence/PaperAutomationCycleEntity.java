package com.tradingplatform.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paper_automation_cycles")
public class PaperAutomationCycleEntity {
    @Id
    public UUID id;

    @Column(name = "cycle_timestamp", nullable = false)
    public Instant timestamp;

    @Column(nullable = false, length = 24)
    public String status;

    @Column(nullable = false, columnDefinition = "text")
    public String reason;

    @Column(length = 16)
    public String direction;

    @Column(name = "option_type", length = 8)
    public String optionType;

    @Column(length = 16)
    public String expiry;

    @Column(precision = 20, scale = 6)
    public BigDecimal strike;

    @Column(name = "signal_strength")
    public Integer signalStrength;

    @Column(name = "paper_order_id")
    public UUID paperOrderId;
}
