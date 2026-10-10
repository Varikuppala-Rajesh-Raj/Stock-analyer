package com.tradingplatform.market.context;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record MarketSessionDefinition(
        String market,
        ZoneId timezone,
        LocalTime sessionOpen,
        LocalTime sessionClose,
        ZoneId collectionTimezone,
        LocalTime collectionStart,
        boolean requiredForNiftyOvernight,
        long maximumLiveQuoteAgeSeconds
) {
    public boolean crossesMidnight() {
        return !sessionClose.isAfter(sessionOpen);
    }

    public SessionWindow sessionOn(ZonedDateTime sessionDate) {
        ZonedDateTime open = sessionDate.withZoneSameInstant(timezone)
                .toLocalDate().atTime(sessionOpen).atZone(timezone);
        ZonedDateTime close = sessionDate.withZoneSameInstant(timezone)
                .toLocalDate().atTime(sessionClose).atZone(timezone);
        if (crossesMidnight()) {
            close = close.plusDays(1);
        }
        return new SessionWindow(market, open, close,
                market + "_" + open.toLocalDate());
    }

    public record SessionWindow(String market, ZonedDateTime open, ZonedDateTime close, String sessionId) {
        public boolean contains(ZonedDateTime timestamp) {
            ZonedDateTime sameZone = timestamp.withZoneSameInstant(open.getZone());
            return !sameZone.isBefore(open) && !sameZone.isAfter(close);
        }
    }
}
