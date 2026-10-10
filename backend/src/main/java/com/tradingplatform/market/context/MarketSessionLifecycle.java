package com.tradingplatform.market.context;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

public final class MarketSessionLifecycle {

    public enum Status {
        LIVE,
        COLLECTING,
        COLLECTED,
        UNAVAILABLE,
        ERROR,
        NOT_CONFIGURED,
        STALE
    }

    public record Assessment(Status status, String sessionId, String requiredSessionId, String detail) {
    }

    private MarketSessionLifecycle() {
    }

    public static Assessment assess(
            MarketSessionDefinition definition,
            Instant now,
            Instant providerTimestamp,
            boolean validData,
            boolean providerError,
            boolean configured
    ) {
        Objects.requireNonNull(now, "now");
        if (!configured || definition == null) {
            return new Assessment(Status.NOT_CONFIGURED, null, null, "Market data provider is not configured.");
        }
        if (providerError) {
            return new Assessment(Status.ERROR, null, null, "Market data provider returned an error.");
        }

        String observedSessionId = dataSessionId(definition, providerTimestamp);
        if (providerTimestamp != null && observedSessionId == null) {
            return new Assessment(Status.STALE, null, null,
                    "Provider timestamp falls outside the configured market session.");
        }

        ZonedDateTime currentMarketTime = now.atZone(definition.timezone());
        MarketSessionDefinition.SessionWindow activeWindow = activeSession(definition, currentMarketTime);
        if (activeWindow != null) {
            if (matchesSession(definition, activeWindow, providerTimestamp, validData)
                    && withinLiveAge(now, providerTimestamp, definition.maximumLiveQuoteAgeSeconds())) {
                return new Assessment(Status.LIVE, activeWindow.sessionId(), activeWindow.sessionId(),
                        "Market session is open and the quote is current.");
            }
            return new Assessment(Status.COLLECTING, observedSessionId,
                    activeWindow.sessionId(), "Market is open; waiting for a valid quote from this session.");
        }

        MarketSessionDefinition.SessionWindow requiredWindow = requiredSession(definition, now);
        boolean collectionCycleStarted = !now.atZone(definition.collectionTimezone()).toLocalTime()
                .isBefore(definition.collectionStart());
        if (!collectionCycleStarted && currentMarketTime.toLocalTime().isBefore(definition.sessionOpen())
                && !definition.crossesMidnight()) {
            return new Assessment(Status.COLLECTING, observedSessionId,
                    requiredWindow.sessionId(), "Waiting for the required market session to open.");
        }
        if (collectionCycleStarted && requiredWindow.open().toInstant().isAfter(now)) {
            return new Assessment(Status.COLLECTING, observedSessionId, requiredWindow.sessionId(),
                    "Preparing data for the next required market session.");
        }
        if (matchesSession(definition, requiredWindow, providerTimestamp, validData)) {
            return new Assessment(Status.COLLECTED, requiredWindow.sessionId(), requiredWindow.sessionId(),
                    "Required completed market session is available.");
        }
        if (providerTimestamp != null && validData && observedSessionId != null) {
            return new Assessment(Status.COLLECTING, observedSessionId,
                    requiredWindow.sessionId(), "Saved quote belongs to a different market session.");
        }
        return new Assessment(Status.UNAVAILABLE, observedSessionId,
                requiredWindow.sessionId(), validData
                        ? "Quote does not belong to the required completed market session."
                        : "Required market-session data has not been collected.");
    }

    public static String dataSessionId(MarketSessionDefinition definition, Instant timestamp) {
        if (definition == null || timestamp == null) {
            return null;
        }
        ZonedDateTime localTimestamp = timestamp.atZone(definition.timezone());
        LocalDate localDate = localTimestamp.toLocalDate();
        for (int offset = -1; offset <= 0; offset++) {
            LocalDate startDate = localDate.plusDays(offset);
            if (!isTradingDay(startDate)) {
                continue;
            }
            MarketSessionDefinition.SessionWindow window =
                    definition.sessionOn(startDate.atStartOfDay(definition.timezone()));
            if (window.contains(localTimestamp)) {
                return window.sessionId();
            }
        }
        return null;
    }

    private static MarketSessionDefinition.SessionWindow activeSession(
            MarketSessionDefinition definition,
            ZonedDateTime current
    ) {
        LocalDate localDate = current.toLocalDate();
        for (int offset = -1; offset <= 0; offset++) {
            LocalDate date = localDate.plusDays(offset);
            if (!isTradingDay(date)) {
                continue;
            }
            MarketSessionDefinition.SessionWindow window =
                    definition.sessionOn(date.atStartOfDay(definition.timezone()));
            if (!current.isBefore(window.open()) && current.isBefore(window.close())) {
                return window;
            }
        }
        return null;
    }

    private static MarketSessionDefinition.SessionWindow requiredSession(
            MarketSessionDefinition definition,
            Instant now
    ) {
        ZonedDateTime cycleTime = now.atZone(definition.collectionTimezone());
        LocalDate marketDate = now.atZone(definition.timezone()).toLocalDate();
        boolean collectingNextCycle = !cycleTime.toLocalTime().isBefore(definition.collectionStart());
        if (collectingNextCycle) {
            for (int offset = 0; offset <= 8; offset++) {
                LocalDate startDate = marketDate.plusDays(offset);
                if (!isTradingDay(startDate)) {
                    continue;
                }
                MarketSessionDefinition.SessionWindow window =
                        definition.sessionOn(startDate.atStartOfDay(definition.timezone()));
                if (!window.open().toInstant().isBefore(now)) {
                    return window;
                }
            }
        } else {
            for (int offset = 0; offset >= -8; offset--) {
                LocalDate startDate = marketDate.plusDays(offset);
                if (!isTradingDay(startDate)) {
                    continue;
                }
                MarketSessionDefinition.SessionWindow window =
                        definition.sessionOn(startDate.atStartOfDay(definition.timezone()));
                if (!window.close().toInstant().isAfter(now)) {
                    return window;
                }
            }
        }
        LocalDate fallback = nextTradingDay(marketDate);
        return definition.sessionOn(fallback.atStartOfDay(definition.timezone()));
    }

    private static boolean matchesSession(
            MarketSessionDefinition definition,
            MarketSessionDefinition.SessionWindow window,
            Instant providerTimestamp,
            boolean validData
    ) {
        return validData && providerTimestamp != null
                && window.sessionId().equals(dataSessionId(definition, providerTimestamp));
    }

    private static boolean withinLiveAge(Instant now, Instant timestamp, long maximumAgeSeconds) {
        Duration age = Duration.between(timestamp, now);
        return !age.isNegative() && age.compareTo(Duration.ofSeconds(maximumAgeSeconds)) <= 0;
    }

    private static LocalDate nextTradingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isTradingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    private static boolean isTradingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
