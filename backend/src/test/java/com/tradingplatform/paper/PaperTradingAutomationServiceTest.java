package com.tradingplatform.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tradingplatform.market.scenario.ScenarioExecutionResult;
import com.tradingplatform.market.scenario.ScenarioExecutionService;
import com.tradingplatform.market.scenario.ScenarioPaperTradeRequest;
import com.tradingplatform.market.scenario.ScenarioTradeDecision;
import com.tradingplatform.market.scenario.MarketScenario;
import com.tradingplatform.persistence.PaperOrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaperTradingAutomationServiceTest {
    @Test
    void persistedEmergencyStopBlocksNewAutomatedOrders() {
        ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
        PaperAutomationControlService control = mock(PaperAutomationControlService.class);
        when(control.isStopped()).thenReturn(true);
        PaperTradingAutomationService service = new PaperTradingAutomationService(execution,
                mock(PaperOrderRepository.class), control, 5, true, 1, 20);

        PaperTradingAutomationDecision result = service.runCycle(validRequest());

        assertFalse(result.allowed());
        assertEquals("AUTOMATION_DISABLED", result.reason());
        assertFalse((Boolean) service.status().get("enabled"));
        verifyNoInteractions(execution);
    }

    @Test
    void submitsFirstFilledTradeAndRejectsDuplicateSignal() {
        ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
        PaperDtos.Order order = filledOrder();
        when(execution.execute(any(), anyDouble(), anyDouble())).thenReturn(
                new ScenarioExecutionResult(
                        new MarketScenario("NIFTY", 22500, "UP", 100, 22600, 15, .75, "VALID"),
                        new ScenarioTradeDecision("ALLOW", true, List.of(), "BUY_CALL", "NIFTY"),
                        order));
        PaperTradingAutomationService service = new PaperTradingAutomationService(execution,
                mock(PaperOrderRepository.class), mock(PaperAutomationControlService.class), 5, true, 1, 20);

        PaperTradingAutomationDecision first = service.runCycle(validRequest());
        PaperTradingAutomationDecision second = service.runCycle(validRequest());

        assertTrue(first.allowed());
        assertTrue(first.tradeSubmitted());
        assertFalse(second.allowed());
        assertEquals("DUPLICATE_SIGNAL", second.reason());
        verify(execution, times(1)).execute(any(), eq(1.0), eq(20.0));
    }

    @Test
    void rejectsWhenAutomationIsDisabled() {
        ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
        PaperTradingAutomationService service = new PaperTradingAutomationService(execution,
                mock(PaperOrderRepository.class), mock(PaperAutomationControlService.class), 5, false, 1, 20);

        PaperTradingAutomationDecision result = service.runCycle(validRequest());

        assertFalse(result.allowed());
        assertEquals("AUTOMATION_DISABLED", result.reason());
        verifyNoInteractions(execution);
    }

    @Test
    void doesNotRecordRejectedPaperOrderAsSubmitted() {
        ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
        PaperDtos.Order rejected = new PaperDtos.Order(UUID.randomUUID(), "NIFTY", PaperSide.BUY, 50,
                BigDecimal.TEN, BigDecimal.TEN, PaperOrderStatus.REJECTED, BigDecimal.ZERO,
                "Maximum allocation exceeded", Instant.now());
        when(execution.execute(any(), anyDouble(), anyDouble())).thenReturn(
                new ScenarioExecutionResult(
                        new MarketScenario("NIFTY", 22500, "UP", 100, 22600, 15, .75, "VALID"),
                        new ScenarioTradeDecision("REJECT", false,
                                List.of("PAPER_ORDER_REJECTED:Maximum allocation exceeded"),
                                "NO_TRADE", "NIFTY"),
                        rejected));
        PaperTradingAutomationService service = new PaperTradingAutomationService(execution,
                mock(PaperOrderRepository.class), mock(PaperAutomationControlService.class), 5, true, 1, 20);

        PaperTradingAutomationDecision result = service.runCycle(validRequest());

        assertFalse(result.allowed());
        assertFalse(result.tradeSubmitted());
        assertTrue(result.reason().startsWith("PAPER_ORDER_REJECTED:"));
    }

    @Test
    void rejectsRecentFilledContractFromPersistentLedgerAfterServiceRestart() {
        ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
        PaperOrderRepository orders = mock(PaperOrderRepository.class);
        when(orders.existsByInstrumentKeyAndCreatedAtAfterAndStatus(
                eq("NSE_FO|NIFTY"), any(), eq(PaperOrderStatus.FILLED))).thenReturn(true);
        PaperTradingAutomationService service = new PaperTradingAutomationService(execution, orders,
                mock(PaperAutomationControlService.class), 5, true, 1, 20);

        PaperTradingAutomationDecision result = service.runCycle(validRequest());

        assertFalse(result.allowed());
        assertEquals("DUPLICATE_SIGNAL", result.reason());
        verifyNoInteractions(execution);
    }

    private static PaperDtos.Order filledOrder() {
        return new PaperDtos.Order(UUID.randomUUID(), "NIFTY", PaperSide.BUY, 50,
                BigDecimal.TEN, BigDecimal.TEN, PaperOrderStatus.FILLED, BigDecimal.ZERO,
                null, Instant.now());
    }

    private static ScenarioPaperTradeRequest validRequest() {
        return new ScenarioPaperTradeRequest("NIFTY", "NSE_FO|NIFTY", "CE", "22500", "2026-10-08",
                50, 22500, "UP", 100, 22600, 15, .75, "VALID", .8, 2,
                .8, 15, true, 50, BigDecimal.TEN, BigDecimal.valueOf(9), BigDecimal.valueOf(12));
    }
}
