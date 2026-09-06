package com.tradingplatform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Central low-cardinality operational counters; secrets and payloads are never recorded. */
@Service
public class ObservabilityService {
    private static final Logger log = LoggerFactory.getLogger(ObservabilityService.class);
    private final MeterRegistry registry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public ObservabilityService(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String event, String component) {
        String safeEvent = normalize(event);
        String safeComponent = normalize(component);
        counters.computeIfAbsent(safeEvent + ":" + safeComponent,
                ignored -> Counter.builder("trading.events")
                        .description("Trading platform operational events")
                        .tag("event", safeEvent)
                        .tag("component", safeComponent)
                        .register(registry)).increment();
        log.info("operational_event event={} component={}", safeEvent, safeComponent);
    }

    public double count(String event, String component) {
        Counter counter = counters.get(normalize(event) + ":" + normalize(component));
        return counter == null ? 0 : counter.count();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9_]+", "_");
    }
}