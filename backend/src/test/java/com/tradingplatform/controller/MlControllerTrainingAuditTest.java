package com.tradingplatform.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.Candle;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.ml.MlPredictionService;
import com.tradingplatform.market.ml.TrainingDatasetAuditService;
import com.tradingplatform.market.ml.TrainingDatasetService;
import com.tradingplatform.market.ml.TrainingExample;
import com.tradingplatform.market.options.OptionTrainingDatasetService;
import com.tradingplatform.signal.AnalysisService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MlControllerTrainingAuditTest {

    @Test
    void blocksTrainingAndReturnsAuditWhenDatasetIsNotReady() {
        MlPredictionService ml = mock(MlPredictionService.class);
        MarketDataService market = mock(MarketDataService.class);
        TrainingDatasetService datasets = mock(TrainingDatasetService.class);
        TrainingDatasetAuditService audits = mock(TrainingDatasetAuditService.class);
        Candle candle = new Candle(Instant.now(), BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO);
        MarketDataService.TrainingHistory history = new MarketDataService.TrainingHistory(List.of(candle), 1, 0);
        TrainingExample example = new TrainingExample(Instant.now(), null, TrainingExample.Target.NEUTRAL,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO);
        ObjectMapper mapper = new ObjectMapper();

        when(market.fetchHistoricalTrainingData(eq("NIFTY"), any(), any(), any())).thenReturn(history);
        when(datasets.generate(anyList(), anyInt(), any())).thenReturn(List.of(example));
        when(audits.audit(any(), any(), eq(history), eq(List.of(example)), anyInt(), anyInt(), any()))
                .thenReturn(Map.of("retrainingRecommendation", "DO_NOT_RETRAIN"));
        when(ml.modelStatus()).thenReturn(mapper.createObjectNode().put("status", "TRAINED_UNVALIDATED"));
        when(ml.magnitudeModelStatus()).thenReturn(mapper.createObjectNode().put("status", "TRAINED_UNVALIDATED"));

        MlController controller = new MlController(
                ml, market, mapper, mock(AnalysisService.class), datasets, audits,
                mock(OptionTrainingDatasetService.class));

        var response = controller.trainNifty(30, 15);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DATA_AUDIT_REQUIRED", response.getBody().path("status").asText());
        assertEquals("DO_NOT_RETRAIN", response.getBody().path("datasetAudit")
                .path("retrainingRecommendation").asText());
        verify(ml, never()).trainNifty(anyList(), anyInt(), anyInt(), any(), anyList());
        verify(ml, never()).trainNiftyMagnitude(anyInt(), anyList());
    }
}