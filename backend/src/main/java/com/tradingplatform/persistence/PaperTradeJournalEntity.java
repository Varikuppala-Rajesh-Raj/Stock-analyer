package com.tradingplatform.persistence;

import com.tradingplatform.paper.PaperPositionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "paper_trade_journal",
        indexes = {
                @Index(name = "idx_trade_journal_account_time", columnList = "account_id, timestamp DESC"),
                @Index(name = "idx_trade_journal_type_time", columnList = "trade_type, timestamp DESC")
        }
)
public class PaperTradeJournalEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "account_id", nullable = false)
    public UUID accountId;

    @Column(name = "trade_id")
    public UUID tradeId;

    @Column(name = "timestamp", nullable = false)
    public Instant timestamp;

    @Column(name = "trade_type", nullable = false)
    public String tradeType;

    @Column(name = "symbol")
    public String symbol;

    @Column(name = "option_symbol")
    public String optionSymbol;

    @Column(name = "option_type")
    public String optionType;

    @Column(name = "strike")
    public String strike;

    @Column(name = "expiry")
    public String expiry;

    @Column(name = "direction")
    public String direction;

    @Column(name = "nifty_entry_level")
    public BigDecimal niftyEntryLevel;

    @Column(name = "nifty_exit_level")
    public BigDecimal niftyExitLevel;

    @Column(name = "expected_move_points")
    public BigDecimal expectedMovePoints;

    @Column(name = "expected_target")
    public BigDecimal expectedTarget;

    @Column(name = "horizon_minutes")
    public Integer horizonMinutes;

    @Column(name = "confidence")
    public BigDecimal confidence;

    @Column(name = "scenario_validity")
    public String scenarioValidity;

    @Column(name = "entry_price")
    public BigDecimal entryPrice;

    @Column(name = "exit_price")
    public BigDecimal exitPrice;

    @Column(name = "quantity")
    public Integer quantity;

    @Column(name = "lot_size")
    public Integer lotSize;

    @Column(name = "gross_pnl")
    public BigDecimal grossPnl;

    @Column(name = "charges")
    public BigDecimal charges;

    @Column(name = "slippage")
    public BigDecimal slippage;

    @Column(name = "net_pnl")
    public BigDecimal netPnl;

    @Column(name = "entry_time")
    public Instant entryTime;

    @Column(name = "exit_time")
    public Instant exitTime;

    @Column(name = "holding_minutes")
    public Long holdingMinutes;

    @Column(name = "exit_reason")
    public String exitReason;

    @Column(name = "risk_decision")
    public String riskDecision;

    @Column(name = "risk_rejection_reasons")
    public String riskRejectionReasons;

    @Column(name = "model_version")
    public String modelVersion;

    @Column(name = "market_regime")
    public String marketRegime;

    @Column(name = "volatility_regime")
    public String volatilityRegime;

    @Column(name = "decision_reason")
    public String decisionReason;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    public PaperPositionStatus status;

    protected PaperTradeJournalEntity() {
    }

    public PaperTradeJournalEntity(
            UUID id,
            UUID accountId,
            UUID tradeId,
            Instant timestamp,
            String tradeType,
            String symbol,
            String optionSymbol,
            String optionType,
            String strike,
            String expiry,
            String direction,
            BigDecimal niftyEntryLevel,
            BigDecimal niftyExitLevel,
            BigDecimal expectedMovePoints,
            BigDecimal expectedTarget,
            Integer horizonMinutes,
            BigDecimal confidence,
            String scenarioValidity,
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            Integer quantity,
            Integer lotSize,
            BigDecimal grossPnl,
            BigDecimal charges,
            BigDecimal slippage,
            BigDecimal netPnl,
            Instant entryTime,
            Instant exitTime,
            Long holdingMinutes,
            String exitReason,
            String riskDecision,
            String riskRejectionReasons,
            String modelVersion,
            String marketRegime,
            String volatilityRegime,
            String decisionReason,
            PaperPositionStatus status
    ) {
        this.id = id;
        this.accountId = accountId;
        this.tradeId = tradeId;
        this.timestamp = timestamp;
        this.tradeType = tradeType;
        this.symbol = symbol;
        this.optionSymbol = optionSymbol;
        this.optionType = optionType;
        this.strike = strike;
        this.expiry = expiry;
        this.direction = direction;
        this.niftyEntryLevel = niftyEntryLevel;
        this.niftyExitLevel = niftyExitLevel;
        this.expectedMovePoints = expectedMovePoints;
        this.expectedTarget = expectedTarget;
        this.horizonMinutes = horizonMinutes;
        this.confidence = confidence;
        this.scenarioValidity = scenarioValidity;
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
        this.quantity = quantity;
        this.lotSize = lotSize;
        this.grossPnl = grossPnl;
        this.charges = charges;
        this.slippage = slippage;
        this.netPnl = netPnl;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.holdingMinutes = holdingMinutes;
        this.exitReason = exitReason;
        this.riskDecision = riskDecision;
        this.riskRejectionReasons = riskRejectionReasons;
        this.modelVersion = modelVersion;
        this.marketRegime = marketRegime;
        this.volatilityRegime = volatilityRegime;
        this.decisionReason = decisionReason;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getTradeId() {
        return tradeId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTradeType() {
        return tradeType;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getOptionSymbol() {
        return optionSymbol;
    }

    public String getOptionType() {
        return optionType;
    }

    public String getStrike() {
        return strike;
    }

    public String getExpiry() {
        return expiry;
    }

    public String getDirection() {
        return direction;
    }

    public BigDecimal getNiftyEntryLevel() {
        return niftyEntryLevel;
    }

    public BigDecimal getNiftyExitLevel() {
        return niftyExitLevel;
    }

    public BigDecimal getExpectedMovePoints() {
        return expectedMovePoints;
    }

    public BigDecimal getExpectedTarget() {
        return expectedTarget;
    }

    public Integer getHorizonMinutes() {
        return horizonMinutes;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getScenarioValidity() {
        return scenarioValidity;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getLotSize() {
        return lotSize;
    }

    public BigDecimal getGrossPnl() {
        return grossPnl;
    }

    public BigDecimal getCharges() {
        return charges;
    }

    public BigDecimal getSlippage() {
        return slippage;
    }

    public BigDecimal getNetPnl() {
        return netPnl;
    }

    public Instant getEntryTime() {
        return entryTime;
    }

    public Instant getExitTime() {
        return exitTime;
    }

    public Long getHoldingMinutes() {
        return holdingMinutes;
    }

    public String getExitReason() {
        return exitReason;
    }

    public String getRiskDecision() {
        return riskDecision;
    }

    public String getRiskRejectionReasons() {
        return riskRejectionReasons;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getMarketRegime() {
        return marketRegime;
    }

    public String getVolatilityRegime() {
        return volatilityRegime;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public PaperPositionStatus getStatus() {
        return status;
    }
}
