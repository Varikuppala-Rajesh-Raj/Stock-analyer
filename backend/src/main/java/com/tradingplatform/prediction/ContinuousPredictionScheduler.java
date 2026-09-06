package com.tradingplatform.prediction;

import com.tradingplatform.observability.ObservabilityService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Runs inference only; model training remains outside the scheduler. */
@Service
public class ContinuousPredictionScheduler {
    private static final Logger log = LoggerFactory.getLogger(ContinuousPredictionScheduler.class);

    private final NiftyPredictionService predictions;
    private final boolean enabled;
    private final int horizonMinutes;
    private final double thresholdPercent;
    private final Clock clock;
    private final ObservabilityService observability;
    private final AtomicBoolean running = new AtomicBoolean();

    public ContinuousPredictionScheduler(
            NiftyPredictionService predictions,
            @Value("${trading.prediction.enabled:false}") boolean enabled,
            @Value("${trading.prediction.horizon-minutes:15}") int horizonMinutes,
            @Value("${trading.prediction.threshold-percent:0.20}") double thresholdPercent,
            ObservabilityService observability
    ) {
        this(predictions, enabled, horizonMinutes, thresholdPercent, Clock.systemUTC(), observability);
    }

    ContinuousPredictionScheduler(NiftyPredictionService predictions, boolean enabled,
                                  int horizonMinutes, double thresholdPercent, Clock clock) {
        this(predictions, enabled, horizonMinutes, thresholdPercent, clock, null);
    }

    ContinuousPredictionScheduler(NiftyPredictionService predictions, boolean enabled,
                                  int horizonMinutes, double thresholdPercent, Clock clock,
                                  ObservabilityService observability) {
        this.predictions = predictions;
        this.enabled = enabled;
        this.horizonMinutes = horizonMinutes;
        this.thresholdPercent = thresholdPercent;
        this.clock = clock;
        this.observability = observability;
    }

    @Scheduled(fixedDelayString = "${trading.prediction.interval-ms:300000}")
    public void scheduledInference() {
        runAt(Instant.now(clock));
    }

    void runAt(Instant timestamp) {
        if (!enabled || PredictionScheduleWindow.at(timestamp) != PredictionScheduleWindow.MARKET) return;
        if (!running.compareAndSet(false, true)) {
            log.debug("Skipping overlapping NIFTY inference run.");
            return;
        }
        try {
            predictions.predict(horizonMinutes, thresholdPercent);
        } catch (RuntimeException error) {
            if (observability != null) observability.record("scheduler_failure", "prediction");
            log.warn("NIFTY inference run failed: {}", error.getMessage());
        } finally {
            running.set(false);
        }
    }

    public Map<String, Object> status() {
        return Map.of("enabled", enabled, "running", running.get(), "horizonMinutes", horizonMinutes,
                "thresholdPercent", thresholdPercent);
    }
}