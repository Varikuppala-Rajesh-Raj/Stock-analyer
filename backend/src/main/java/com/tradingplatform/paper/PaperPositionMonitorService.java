package com.tradingplatform.paper;

import com.tradingplatform.persistence.PaperPositionEntity;
import com.tradingplatform.persistence.PaperPositionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaperPositionMonitorService {

    private final PaperPositionRepository positionRepository;
    private final Duration maxHoldingWindow;

    public PaperPositionMonitorService(
            PaperPositionRepository positionRepository,
            @Value("${trading.paper.max-holding-minutes:240}") long maxHoldingMinutes
    ) {
        this.positionRepository = positionRepository;
        this.maxHoldingWindow = Duration.ofMinutes(maxHoldingMinutes);
    }

    public int monitorOpenPositions() {
        return monitorOpenPositions(maxHoldingWindow);
    }

    public int monitorOpenPositions(Duration maxHoldingWindow) {
        List<PaperPositionEntity> openPositions = positionRepository.findByStatus(PaperPositionStatus.OPEN);
        int updated = 0;

        for (PaperPositionEntity position : openPositions) {
            BigDecimal currentPrice = position.currentPrice;
            if (currentPrice == null) {
                continue;
            }

            boolean shouldExit = false;
            String exitReason = null;

            if (position.side == PaperSide.BUY) {
                if (position.target != null && currentPrice.compareTo(position.target) >= 0) {
                    shouldExit = true;
                    exitReason = "TARGET_HIT";
                } else if (position.stopLoss != null && currentPrice.compareTo(position.stopLoss) <= 0) {
                    shouldExit = true;
                    exitReason = "STOP_LOSS";
                }
            } else if (position.side == PaperSide.SELL) {
                if (position.target != null && currentPrice.compareTo(position.target) <= 0) {
                    shouldExit = true;
                    exitReason = "TARGET_HIT";
                } else if (position.stopLoss != null && currentPrice.compareTo(position.stopLoss) >= 0) {
                    shouldExit = true;
                    exitReason = "STOP_LOSS";
                }
            }

            if (!shouldExit && position.openedAt != null && Duration.between(position.openedAt, Instant.now()).compareTo(maxHoldingWindow) > 0) {
                shouldExit = true;
                exitReason = "MAX_HOLD_TIME";
            }

            if (shouldExit) {
                position.status = PaperPositionStatus.CLOSED;
                position.updatedAt = Instant.now();
                positionRepository.save(position);
                updated++;
            }
        }

        return updated;
    }
}
