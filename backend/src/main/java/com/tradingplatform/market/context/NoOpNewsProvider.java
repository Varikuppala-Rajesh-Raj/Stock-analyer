package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default provider until a configured news API implementation is supplied. */
@Component
@ConditionalOnProperty(name = "trading.news.provider", havingValue = "noop")
public class NoOpNewsProvider implements NewsProvider {
    @Override
    public List<NewsItem> fetch(Instant asOf) {
        return List.of();
    }
}
