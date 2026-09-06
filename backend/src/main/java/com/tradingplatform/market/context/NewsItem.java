package com.tradingplatform.market.context;

import java.time.Instant;
import java.util.List;

/** Raw news metadata retained before conversion to numerical features. */
public record NewsItem(String headline, String source, String url,
                       Instant publicationTimestamp, Instant ingestionTimestamp,
                       List<String> affectedInstruments, String category,
                       Double sentiment, Double relevance, String eventType) {
    public NewsItem {
        affectedInstruments = affectedInstruments == null ? List.of() : List.copyOf(affectedInstruments);
    }
}