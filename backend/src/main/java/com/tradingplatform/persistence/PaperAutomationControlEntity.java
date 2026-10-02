package com.tradingplatform.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "paper_automation_control")
public class PaperAutomationControlEntity {
    @Id
    public Integer id;

    @Column(nullable = false)
    public boolean stopped;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
