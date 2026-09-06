package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Default provider until a configured news API implementation is supplied. */
@Component
public class NoOpNewsProvider implements NewsProvider {
    @Override
    public List<NewsItem> fetch(Instant asOf) {
        return List.of();
    }
}