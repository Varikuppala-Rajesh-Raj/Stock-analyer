package com.tradingplatform.paper;

import com.tradingplatform.market.InstrumentCatalogService;
import com.tradingplatform.persistence.PaperPositionEntity;
import com.tradingplatform.persistence.PaperPositionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaperPositionMonitorService {
    private static final Logger log = LoggerFactory.getLogger(PaperPositionMonitorService.class);
    private final PaperPositionRepository positionRepository;
    private final InstrumentCatalogService instruments;
    private final PaperPriceService prices;
    private final PaperTradingEngine paper;
    private final Duration maxHoldingWindow;

    public PaperPositionMonitorService(
            PaperPositionRepository positionRepository,
            InstrumentCatalogService instruments,
            PaperPriceService prices,
            PaperTradingEngine paper,
            @Value("${trading.paper.max-holding-minutes:240}") long maxHoldingMinutes
    ) {
        this.positionRepository = positionRepository;
        this.instruments = instruments;
        this.prices = prices;
        this.paper = paper;
        this.maxHoldingWindow = Duration.ofMinutes(maxHoldingMinutes);
    }

    @Scheduled(fixedDelayString = "${trading.paper.position-monitor-interval-ms:30000}")
    public void scheduledMonitor() {
        monitorOpenPositions();
    }

    public int monitorOpenPositions() {
        return monitorOpenPositions(maxHoldingWindow);
    }

    public int monitorOpenPositions(Duration holdingWindow) {
        if (holdingWindow == null || holdingWindow.isNegative()) {
            throw new IllegalArgumentException("Maximum holding window must be non-negative.");
        }
        List<PaperPositionEntity> openPositions = positionRepository.findByStatus(PaperPositionStatus.OPEN);
        int updated = 0;
        for (PaperPositionEntity position : openPositions) {
            try {
                if (position.openedAt != null
                        && Duration.between(position.openedAt, Instant.now()).compareTo(holdingWindow) > 0) {
                    PaperDtos.Order close = paper.close(position.id);
                    if (close.status() == PaperOrderStatus.FILLED) updated++;
                    else log.warn("Paper position {} could not be closed at max holding time: {}",
                            position.id, close.rejectionReason());
                    continue;
                }
                var instrument = instruments.resolveSymbol(position.instrumentKey);
                var price = prices.current(instrument);
                paper.onPrice(position.instrumentKey, price.value());
                updated++;
            } catch (RuntimeException exception) {
                log.warn("Unable to refresh paper position {} using fresh market data: {}",
                        position.id, exception.getMessage());
            }
        }
        return updated;
    }
}
