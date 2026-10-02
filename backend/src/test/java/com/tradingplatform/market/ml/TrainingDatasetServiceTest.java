package com.tradingplatform.market.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingplatform.market.Candle;
import com.tradingplatform.market.indicators.FeatureVector;
import com.tradingplatform.market.indicators.TechnicalFeatureService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrainingDatasetServiceTest {

    @Test
    void generateSkipsLabelsWhoseElapsedTimeDoesNotMatchHorizon() {
        TechnicalFeatureService featureService = mock(TechnicalFeatureService.class);
        when(featureService.calculate(anyList())).thenReturn(features());
        TrainingDatasetService datasets = new TrainingDatasetService(featureService);
        Instant start = Instant.parse("2024-01-01T09:15:00Z");
        List<Candle> candles = new ArrayList<>();

        for (int index = 0; index < 65; index++) {
            long minutes = index * 5L + (index >= 60 ? 10L : 0L);
            candles.add(candle(start.plusSeconds(minutes * 60)));
        }

        List<TrainingExample> examples = datasets.generate(
                candles,
                3,
                new BigDecimal("0.20")
        );

        assertEquals(2, examples.size());
        assertEquals(candles.get(60).timestamp(), examples.get(0).timestamp());
        assertEquals(candles.get(61).timestamp(), examples.get(1).timestamp());
    }

    @Test
    void featureWindowsMatchTheConfiguredLiveAnalysisLimit() {
        TechnicalFeatureService featureService = mock(TechnicalFeatureService.class);
        List<Integer> featureWindowSizes = new ArrayList<>();
        when(featureService.calculate(anyList())).thenAnswer(invocation -> {
            featureWindowSizes.add(((List<Candle>) invocation.getArgument(0)).size());
            return features();
        });
        TrainingDatasetService datasets = new TrainingDatasetService(featureService, 500);
        Instant start = Instant.parse("2025-01-06T03:45:00Z");
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < 600; index++) {
            candles.add(candle(start.plusSeconds(index * 5L * 60)));
        }

        datasets.generate(candles, 3, new BigDecimal("0.20"));

        assertEquals(500, featureWindowSizes.get(featureWindowSizes.size() - 1));
        assertTrue(featureWindowSizes.stream().allMatch(size -> size <= 500));
    }

    @Test
    void weekendAndOvernightTargetsAreNotMatchedAsFifteenMinuteLabels() {
        TechnicalFeatureService featureService = mock(TechnicalFeatureService.class);
        when(featureService.calculate(anyList())).thenReturn(features());
        TrainingDatasetService datasets = new TrainingDatasetService(featureService);
        ZoneId india = ZoneId.of("Asia/Kolkata");
        List<Candle> candles = new ArrayList<>();
        appendSession(candles, LocalDate.of(2025, 1, 3), india);
        appendSession(candles, LocalDate.of(2025, 1, 6), india);

        List<TrainingExample> examples = datasets.generate(
                candles, 3, new BigDecimal("0.20"));

        assertEquals(85, examples.size());
        assertTrue(examples.stream().allMatch(example ->
                Duration.between(example.timestamp(), example.timestamp().plusSeconds(15 * 60))
                        .equals(Duration.ofMinutes(15))));
        assertTrue(examples.stream().noneMatch(example ->
                example.timestamp().atZone(india).toLocalDate().equals(LocalDate.of(2025, 1, 3))
                        && example.timestamp().atZone(india).toLocalTime().isAfter(java.time.LocalTime.of(15, 10))));
    }

                @Test
                void weekdayClosureGapDoesNotBecomeFifteenMinuteTarget() {
                TechnicalFeatureService featureService = mock(TechnicalFeatureService.class);
                when(featureService.calculate(anyList())).thenReturn(features());
                TrainingDatasetService datasets = new TrainingDatasetService(featureService);
                ZoneId india = ZoneId.of("Asia/Kolkata");
                List<Candle> candles = new ArrayList<>();
                appendSession(candles, LocalDate.of(2025, 1, 7), india);
                appendSession(candles, LocalDate.of(2025, 1, 9), india);

                List<TrainingExample> examples = datasets.generate(
                    candles, 3, new BigDecimal("0.20"));

                assertEquals(85, examples.size());
                assertEquals(13, examples.stream().filter(example ->
                    example.timestamp().atZone(india).toLocalDate().equals(LocalDate.of(2025, 1, 7))).count());
                assertTrue(examples.stream().filter(example ->
                    example.timestamp().atZone(india).toLocalDate().equals(LocalDate.of(2025, 1, 7)))
                    .allMatch(example -> example.timestamp().atZone(india).toLocalTime()
                        .isBefore(java.time.LocalTime.of(15, 15))));
                assertEquals(72, examples.stream().filter(example ->
                    example.timestamp().atZone(india).toLocalDate().equals(LocalDate.of(2025, 1, 9))).count());
                }

    @Test
    void duplicateTimestampsAreRemovedAndFeatureWindowsStopAtPredictionTime() {
        TechnicalFeatureService featureService = mock(TechnicalFeatureService.class);
        List<Instant> featureWindowEnds = new ArrayList<>();
        when(featureService.calculate(anyList())).thenAnswer(invocation -> {
            List<Candle> window = invocation.getArgument(0);
            featureWindowEnds.add(window.get(window.size() - 1).timestamp());
            return features();
        });
        TrainingDatasetService datasets = new TrainingDatasetService(featureService);
        Instant start = Instant.parse("2025-01-06T03:45:00Z");
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < 70; index++) {
            candles.add(candle(start.plusSeconds(index * 5L * 60)));
        }
        candles.add(candle(start.plusSeconds(40 * 5L * 60)));
        java.util.Collections.reverse(candles);

        List<TrainingExample> examples = datasets.generate(
                candles, 3, new BigDecimal("0.20"));

        assertEquals(8, examples.size());
        assertEquals(examples.stream().map(TrainingExample::timestamp).toList(), featureWindowEnds);
        assertTrue(examples.stream().allMatch(example ->
                example.timestamp().plusSeconds(15 * 60).isAfter(example.timestamp())));
    }

    private void appendSession(List<Candle> candles, LocalDate date, ZoneId zone) {
        Instant start = date.atTime(9, 15).atZone(zone).toInstant();
        for (int index = 0; index < 75; index++) {
            candles.add(candle(start.plusSeconds(index * 5L * 60)));
        }
    }

    private FeatureVector features() {
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal price = BigDecimal.valueOf(100);
        Map<String, BigDecimal> strategyFeatures = new java.util.LinkedHashMap<>();
        com.tradingplatform.market.indicators.CanonicalTechnicalFeatures.NAMES
            .forEach(name -> strategyFeatures.put(name, BigDecimal.ONE));
        return new FeatureVector(price, price, price, price, price, price,
            price, zero, zero, zero, zero, zero, zero, zero, zero,
            zero, strategyFeatures);
    }

    private Candle candle(Instant timestamp) {
        BigDecimal price = BigDecimal.valueOf(100);
        return new Candle(timestamp, price, price, price, price, BigDecimal.ONE);
    }
}