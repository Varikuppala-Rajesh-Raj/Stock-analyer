package com.tradingplatform.market;

import java.util.Arrays;

public enum Timeframe {
    M1("minutes", 1, true), M3("minutes", 3, true), M5("minutes", 5, true),
    M15("minutes", 15, true), M30("minutes", 30, true), H1("hours", 1, true),
    D1("days", 1, false), W1("weeks", 1, false);
    private final String unit; private final int interval; private final boolean intraday;
    Timeframe(String unit, int interval, boolean intraday) { this.unit = unit; this.interval = interval; this.intraday = intraday; }
    public String unit() { return unit; } public int interval() { return interval; } public boolean supportsIntraday() { return intraday; }
    public static Timeframe parse(String value) {
        String normalized = switch (value.toLowerCase()) { case "1m" -> "M1"; case "3m" -> "M3"; case "5m" -> "M5"; case "15m" -> "M15"; case "30m" -> "M30"; case "1h" -> "H1"; case "1d" -> "D1"; case "1w" -> "W1"; default -> value; };
        return Arrays.stream(values()).filter(t -> t.name().equalsIgnoreCase(normalized))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Unsupported timeframe: " + value));
    }
}
