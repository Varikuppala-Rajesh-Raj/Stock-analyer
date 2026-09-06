package com.tradingplatform.market.context;

import java.time.Instant;
import org.springframework.stereotype.Service;

/** Retrieves provider data and enforces the prediction-time news cutoff. */
@Service
public class NewsContextService {
    private final NewsProvider provider;

    public NewsContextService(NewsProvider provider) {
        this.provider = provider;
    }

    public NewsContext snapshot(Instant asOf) {
        return NewsFeatureCalculator.calculate(asOf, provider.fetch(asOf));
    }
}