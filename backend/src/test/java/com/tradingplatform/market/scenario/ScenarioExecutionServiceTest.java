package com.tradingplatform.market.scenario;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.tradingplatform.paper.PaperDtos;
import com.tradingplatform.paper.PaperOrderStatus;
import com.tradingplatform.paper.PaperSide;
import com.tradingplatform.paper.PaperTradeJournalService;
import com.tradingplatform.paper.PaperTradingEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioExecutionServiceTest {

    @Test
    void validScenarioSubmitsPaperOrder() {
        PaperTradingEngine paper = mock(PaperTradingEngine.class);
        when(paper.submit(any())).thenReturn(new PaperDtos.Order(
                UUID.randomUUID(),
                "NIFTY",
                PaperSide.BUY,
                100,
                BigDecimal.valueOf(22800),
                BigDecimal.valueOf(22800),
                PaperOrderStatus.FILLED,
                BigDecimal.ZERO,
                null,
                Instant.now()
        ));

        PaperTradeJournalService journal = mock(PaperTradeJournalService.class);
        ScenarioExecutionService service = new ScenarioExecutionService(
                paper,
                new MarketScenarioRiskGateService(),
                new ScenarioPaperTradeService(),
                journal
        );

        ScenarioPaperTradeRequest request = new ScenarioPaperTradeRequest(
                "NIFTY",
                "NIFTY24000CE",
                "CE",
                "24000",
                "2025-06-26",
                15,
                22800.0,
                "UP",
                180.0,
                22980.0,
                15,
                0.68,
                "VALID",
                0.9,
                1.8,
                1.5,
                1.5,
                true,
                100,
                BigDecimal.valueOf(22800),
                BigDecimal.valueOf(22770),
                BigDecimal.valueOf(22980)
        );

        ScenarioExecutionResult result = service.execute(request);

        assertNotNull(result);
        assertTrue(result.allowed());
        assertEquals(ScenarioTradeDecision.ALLOW, result.decision().status());
        assertNotNull(result.order());
        verify(paper, times(1)).submit(any());
    }

    @Test
    void invalidScenarioIsRejectedBeforePaperOrder() {
        PaperTradingEngine paper = mock(PaperTradingEngine.class);
        PaperTradeJournalService journal = mock(PaperTradeJournalService.class);
        ScenarioExecutionService service = new ScenarioExecutionService(
                paper,
                new MarketScenarioRiskGateService(),
                new ScenarioPaperTradeService(),
                journal
        );

        ScenarioPaperTradeRequest request = new ScenarioPaperTradeRequest(
                "NIFTY",
                "NIFTY24000CE",
                "CE",
                "24000",
                "2025-06-26",
                15,
                22800.0,
                "NEUTRAL",
                10.0,
                22810.0,
                15,
                0.40,
                "LOW_CONFIDENCE",
                0.4,
                1.0,
                1.0,
                1.0,
                true,
                100,
                BigDecimal.valueOf(22800),
                BigDecimal.valueOf(22770),
                BigDecimal.valueOf(22810)
        );

        ScenarioExecutionResult result = service.execute(request);

        assertNotNull(result);
        assertFalse(result.allowed());
        assertEquals(ScenarioTradeDecision.REJECT, result.decision().status());
        assertNull(result.order());
        verify(paper, never()).submit(any());
    }
}
