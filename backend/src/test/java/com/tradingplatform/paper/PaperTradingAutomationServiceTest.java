package com.tradingplatform.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.market.scenario.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaperTradingAutomationServiceTest {

    @Test
    void allowsFirstTradeAndRejectsDuplicateSignalWithinWindow() {
        ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
                MlPredictionService ml = validatedModels();
        when(execution.execute(any())).thenReturn(new ScenarioExecutionResult(
                new MarketScenario("NIFTY", 22500.0, "UP", 120.0, 22620.0, 15, 0.72, "VALID"),
                new ScenarioTradeDecision(ScenarioTradeDecision.ALLOW, true, java.util.List.of(), "BUY_CALL", "NIFTY"),
                new PaperDtos.Order(
                        UUID.randomUUID(),
                        "NIFTY24000CE",
                        PaperSide.BUY,
                        1,
                        BigDecimal.valueOf(145.25),
                        BigDecimal.valueOf(145.25),
                        PaperOrderStatus.FILLED,
                        BigDecimal.ZERO,
                        null,
                        Instant.now()
                )
        ));

        PaperTradingAutomationService service = new PaperTradingAutomationService(
                execution,
                ml,
                Duration.ofMinutes(5),
                true
        );

        ScenarioPaperTradeRequest request = new ScenarioPaperTradeRequest(
                "NIFTY",
                "NIFTY24000CE",
                "CE",
                "24000",
                "2025-06-26",
                1,
                22500.0,
                "UP",
                120.0,
                22620.0,
                15,
                0.72,
                "VALID",
                0.9,
                1.8,
                1.5,
                1.5,
                true,
                1,
                BigDecimal.valueOf(145.25),
                BigDecimal.valueOf(144.00),
                BigDecimal.valueOf(146.50)
        );

        PaperTradingAutomationDecision first = service.runCycle(request);
        PaperTradingAutomationDecision second = service.runCycle(request);

        assertTrue(first.allowed());
        assertFalse(second.allowed());
        assertEquals("DUPLICATE_SIGNAL", second.reason());
        verify(execution, times(1)).execute(any());
    }

    @Test
    void rejectsWhenMarketClosed() {
        ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
        PaperTradingAutomationService service = new PaperTradingAutomationService(
                execution,
                                validatedModels(),
                Duration.ofMinutes(5),
                true
        );

        ScenarioPaperTradeRequest request = new ScenarioPaperTradeRequest(
                "NIFTY",
                "NIFTY24000CE",
                "CE",
                "24000",
                "2025-06-26",
                1,
                22500.0,
                "UP",
                120.0,
                22620.0,
                15,
                0.72,
                "VALID",
                0.9,
                1.8,
                1.5,
                1.5,
                false,
                1,
                BigDecimal.valueOf(145.25),
                BigDecimal.valueOf(144.00),
                BigDecimal.valueOf(146.50)
        );

        PaperTradingAutomationDecision result = service.runCycle(request);

        assertFalse(result.allowed());
        assertEquals("MARKET_CLOSED", result.reason());
        verify(execution, never()).execute(any());
    }

        @Test
        void rejectsAutomationWhenAnyModelHasOnlySyntheticOrUnvalidatedStatus() {
                ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
                MlPredictionService ml = validatedModels();
                ObjectNode unvalidated = validationMetadata();
                unvalidated.put("status", "SYNTHETIC_BASELINE");
                unvalidated.put("eligibleForAutomation", false);
                when(ml.optionMagnitudeModelStatus()).thenReturn(unvalidated);
                PaperTradingAutomationService service = new PaperTradingAutomationService(
                                execution, ml, Duration.ofMinutes(5), true);

                PaperTradingAutomationDecision result = service.runCycle(validRequest());

                assertFalse(result.allowed());
                assertEquals("OPTION_MODEL_NOT_VALIDATED", result.reason());
                verify(execution, never()).execute(any());
        }

        @Test
        void rejectsAutomationWhenMlServiceIsUnavailable() {
                ScenarioExecutionService execution = mock(ScenarioExecutionService.class);
                MlPredictionService ml = mock(MlPredictionService.class);
                when(ml.modelStatus()).thenThrow(new IllegalStateException("connection refused"));
                PaperTradingAutomationService service = new PaperTradingAutomationService(
                                execution, ml, Duration.ofMinutes(5), true);

                PaperTradingAutomationDecision result = service.runCycle(validRequest());

                assertFalse(result.allowed());
                assertEquals("ML_SERVICE_UNAVAILABLE", result.reason());
                verify(execution, never()).execute(any());
        }

        private MlPredictionService validatedModels() {
                MlPredictionService ml = mock(MlPredictionService.class);
                ObjectNode metadata = validationMetadata();
                when(ml.modelStatus()).thenReturn(metadata);
                when(ml.magnitudeModelStatus()).thenReturn(metadata);
                when(ml.optionMagnitudeModelStatus()).thenReturn(metadata);
                return ml;
        }

        private ObjectNode validationMetadata() {
                ObjectNode metadata = new ObjectMapper().createObjectNode();
                metadata.put("status", "VALIDATED");
                metadata.put("validationStatus", "VALIDATED");
                metadata.put("eligibleForAutomation", true);
                metadata.put("modelVersion", "test-model-v1");
                metadata.put("validationMethod", "chronological 80/20 holdout");
                metadata.put("trainingRows", 100);
                metadata.put("validationRows", 20);
                return metadata;
        }

        private ScenarioPaperTradeRequest validRequest() {
                return new ScenarioPaperTradeRequest(
                                "NIFTY", "NIFTY24000CE", "CE", "24000", "2025-06-26", 1,
                                22500.0, "UP", 120.0, 22620.0, 15, 0.72, "VALID",
                                0.9, 1.8, 1.5, 1.5, true, 1,
                                BigDecimal.valueOf(145.25), BigDecimal.valueOf(144.00), BigDecimal.valueOf(146.50));
        }
}
