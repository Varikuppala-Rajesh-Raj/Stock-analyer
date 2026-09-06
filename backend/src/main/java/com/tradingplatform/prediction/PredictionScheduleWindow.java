package com.tradingplatform.prediction;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/** Session classification used by the continuous inference scheduler. */
public enum PredictionScheduleWindow {
    PRE_MARKET,
    MARKET,
    POST_MARKET,
    OVERNIGHT;

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    public static PredictionScheduleWindow at(Instant timestamp) {
        LocalTime time = timestamp.atZone(INDIA).toLocalTime();
        if (time.isBefore(LocalTime.of(9, 15)) && !time.isBefore(LocalTime.of(8, 0))) return PRE_MARKET;
        if (!time.isBefore(LocalTime.of(9, 15)) && !time.isAfter(LocalTime.of(15, 30))) return MARKET;
        if (time.isAfter(LocalTime.of(15, 30)) && !time.isAfter(LocalTime.of(18, 0))) return POST_MARKET;
        return OVERNIGHT;
    }
}