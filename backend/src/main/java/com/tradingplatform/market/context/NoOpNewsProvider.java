package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Default provider until a configured news API implementation is supplied. */
@Component
@ConditionalOnExpression("'${trading.news.provider:marketaux}' == 'noop' or ('${trading.news.provider:marketaux}' == 'marketaux' and '${trading.news.marketaux.api-key:}' == '')")
public class NoOpNewsProvider implements NewsProvider {
    @Override
    public List<NewsItem> fetch(Instant asOf) {
        return List.of();
    }
}
