package com.tradingplatform.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.nse.NseMarketContextService;
import com.tradingplatform.market.options.NiftyOptionChainService;
import com.tradingplatform.market.options.OptionContractSelector;
import com.tradingplatform.market.scenario.ScenarioExecutionService;
import com.tradingplatform.persistence.PaperAutomationCycleEntity;
import com.tradingplatform.persistence.PaperAutomationCycleRepository;
import com.tradingplatform.persistence.PaperOrderRepository;
import com.tradingplatform.signal.AnalysisService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NiftyPaperTradingCycleServiceTest {
    @Test
    void recordsExplicitDisabledCycleWithoutFetchingMarketData() {
        MarketDataService market = mock(MarketDataService.class);
        PaperAutomationCycleRepository audit = mock(PaperAutomationCycleRepository.class);
        PaperTradingAutomationService automation = new PaperTradingAutomationService(
                mock(ScenarioExecutionService.class), mock(PaperOrderRepository.class),
                mock(PaperAutomationControlService.class), 5, false, 1, 20);
        NiftyPaperTradingCycleService service = new NiftyPaperTradingCycleService(
                market, mock(AnalysisService.class), mock(NseMarketContextService.class),
                mock(NiftyOptionChainService.class), mock(OptionContractSelector.class),
                mock(PaperTradingEngine.class), mock(PaperPriceService.class), automation, audit, 60,
                BigDecimal.valueOf(30), BigDecimal.valueOf(2), BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(2), BigDecimal.valueOf(.2));

        NiftyPaperTradingCycleResult result = service.runOnce();

        assertEquals("DISABLED", result.status());
        assertEquals("AUTOMATION_DISABLED", result.reason());
        verify(audit).save(any(PaperAutomationCycleEntity.class));
        verifyNoInteractions(market);
    }

    @Test
    void refusesMockMarketDataBeforeUsingSignalOrOptionServices() {
        MarketDataService market = mock(MarketDataService.class);
        when(market.usesLiveProvider()).thenReturn(false);
        PaperAutomationCycleRepository audit = mock(PaperAutomationCycleRepository.class);
        PaperTradingAutomationService automation = new PaperTradingAutomationService(
                mock(ScenarioExecutionService.class), mock(PaperOrderRepository.class),
                mock(PaperAutomationControlService.class), 5, true, 1, 20);
        NiftyPaperTradingCycleService service = new NiftyPaperTradingCycleService(
                market, mock(AnalysisService.class), mock(NseMarketContextService.class),
                mock(NiftyOptionChainService.class), mock(OptionContractSelector.class),
                mock(PaperTradingEngine.class), mock(PaperPriceService.class), automation, audit, 60,
                BigDecimal.valueOf(30), BigDecimal.valueOf(2), BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(2), BigDecimal.valueOf(.2));

        NiftyPaperTradingCycleResult result = service.runOnce();

        assertEquals("NO_TRADE", result.status());
        assertEquals("LIVE_MARKET_DATA_REQUIRED", result.reason());
        verify(audit).save(any(PaperAutomationCycleEntity.class));
        verify(market).usesLiveProvider();
        verifyNoMoreInteractions(market);
    }
}
