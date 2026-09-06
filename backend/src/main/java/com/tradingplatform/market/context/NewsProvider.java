package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.List;

/** Provider boundary for configurable news APIs. */
public interface NewsProvider {
    List<NewsItem> fetch(Instant asOf);
}