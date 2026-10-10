package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MarketSessionLifecycleTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final MarketSessionDefinition OVERNIGHT = new MarketSessionDefinition(
            "GLOBAL_X", IST, LocalTime.of(18, 0), LocalTime.of(1, 0),
            IST, LocalTime.of(15, 30), true, 300);

    @Test
    void beforeOpenWaitsForTodaySession() {
        var result = assess("2026-10-08T17:00:00+05:30", null, false);

        assertEquals(MarketSessionLifecycle.Status.COLLECTING, result.status());
        assertEquals("GLOBAL_X_2026-10-08", result.requiredSessionId());
    }

    @Test
    void duringSessionIsLiveOnlyForARecentValidQuote() {
        var live = assess("2026-10-08T20:00:00+05:30", "2026-10-08T19:59:00+05:30", true);
        var waiting = assess("2026-10-08T20:00:00+05:30", null, false);

        assertEquals(MarketSessionLifecycle.Status.LIVE, live.status());
        assertEquals(MarketSessionLifecycle.Status.COLLECTING, waiting.status());
    }

    @Test
    void closedSessionIsCollectedAtOneThirtyAndEightAmRegardlessOfAge() {
        Instant marketCloseQuote = Instant.parse("2026-10-08T19:25:00Z");
        var justClosed = MarketSessionLifecycle.assess(OVERNIGHT,
                Instant.parse("2026-10-08T20:00:00Z"), marketCloseQuote, true, false, true);
        var morning = MarketSessionLifecycle.assess(OVERNIGHT,
                Instant.parse("2026-10-09T02:30:00Z"), marketCloseQuote, true, false, true);

        assertEquals(MarketSessionLifecycle.Status.COLLECTED, justClosed.status());
        assertEquals(MarketSessionLifecycle.Status.COLLECTED, morning.status());
        assertEquals(justClosed.requiredSessionId(), morning.requiredSessionId());
    }

    @Test
    void indianCloseStartsTheNextCollectionCycle() {
        var result = assess("2026-10-08T15:31:00+05:30", null, false);

        assertEquals(MarketSessionLifecycle.Status.COLLECTING, result.status());
        assertEquals("GLOBAL_X_2026-10-08", result.requiredSessionId());
    }

    @Test
    void overnightSessionUsesItsStartDateAsLogicalSessionId() {
        String sessionId = MarketSessionLifecycle.dataSessionId(
                OVERNIGHT, Instant.parse("2026-10-08T14:30:00Z"));

        assertEquals("GLOBAL_X_2026-10-08", sessionId);
        assertTrue(OVERNIGHT.crossesMidnight());
        assertEquals(Instant.parse("2026-10-08T19:30:00Z"),
                OVERNIGHT.sessionOn(LocalDate.parse("2026-10-08").atStartOfDay(IST))
                        .close().toInstant());
    }

    @Test
    void providerFailureAndMissingDataNeverBecomeCollected() {
        var error = MarketSessionLifecycle.assess(OVERNIGHT,
                Instant.parse("2026-10-09T02:30:00Z"), null, false, true, true);
        var missing = MarketSessionLifecycle.assess(OVERNIGHT,
                Instant.parse("2026-10-09T02:30:00Z"), null, false, false, true);

        assertEquals(MarketSessionLifecycle.Status.ERROR, error.status());
        assertEquals(MarketSessionLifecycle.Status.UNAVAILABLE, missing.status());
    }

    @Test
    void oldButCorrectCompletedSessionRemainsCollected() {
        var result = assess("2026-10-09T08:00:00+05:30", "2026-10-08T20:00:00+05:30", true);

        assertEquals(MarketSessionLifecycle.Status.COLLECTED, result.status());
    }

    @Test
    void quoteFromPreviousSessionDoesNotSatisfyNewCycle() {
        var result = assess("2026-10-09T17:00:00+05:30", "2026-10-08T20:00:00+05:30", true);

        assertEquals(MarketSessionLifecycle.Status.COLLECTING, result.status());
        assertEquals("GLOBAL_X_2026-10-09", result.requiredSessionId());
    }

    @Test
    void unconfiguredMarketHasSeparateState() {
        var result = MarketSessionLifecycle.assess(OVERNIGHT, Instant.now(), null, false, false, false);

        assertEquals(MarketSessionLifecycle.Status.NOT_CONFIGURED, result.status());
    }

    private static MarketSessionLifecycle.Assessment assess(
            String now,
            String providerTimestamp,
            boolean validData
    ) {
        return MarketSessionLifecycle.assess(OVERNIGHT, Instant.parse(now),
                providerTimestamp == null ? null : Instant.parse(providerTimestamp),
                validData, false, true);
    }
}
