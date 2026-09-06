package com.tradingplatform.market.context;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

/** Converts timestamp-safe news items into deterministic numerical context. */
public final class NewsFeatureCalculator {
    private NewsFeatureCalculator() {
    }

    public static NewsContext calculate(Instant asOf, List<NewsItem> source) {
        List<NewsItem> available = source == null ? List.of() : source.stream()
                .filter(item -> item != null && item.publicationTimestamp() != null)
                .filter(item -> !item.publicationTimestamp().isAfter(asOf))
                .toList();
        return new NewsContext(asOf,
                average(available, asOf, Duration.ofMinutes(15), item -> true),
                average(available, asOf, Duration.ofHours(1), item -> true),
                (double) count(available, asOf, Duration.ofMinutes(15)),
                (double) count(available, asOf, Duration.ofHours(1)),
                average(available, asOf, Duration.ofHours(1), item -> category(item, "india")),
                average(available, asOf, Duration.ofHours(1), item -> category(item, "global")),
                risk(available, asOf, Duration.ofHours(1), "geopolitics"),
                event(available, asOf, Duration.ofHours(1), "rbi"),
                event(available, asOf, Duration.ofHours(1), "fed"),
                risk(available, asOf, Duration.ofHours(1), "macro"));
    }

    private static long count(List<NewsItem> items, Instant asOf, Duration window) {
        Instant start = asOf.minus(window);
        return items.stream().filter(item -> inWindow(item, start, asOf)).count();
    }

    private static Double average(List<NewsItem> items, Instant asOf, Duration window, Predicate<NewsItem> filter) {
        Instant start = asOf.minus(window);
        double[] values = items.stream().filter(item -> inWindow(item, start, asOf) && filter.test(item))
                .filter(item -> item.sentiment() != null && Double.isFinite(item.sentiment()))
                .mapToDouble(item -> item.sentiment() * relevance(item)).toArray();
        return values.length == 0 ? null : java.util.Arrays.stream(values).average().orElse(0.0);
    }

    private static Double risk(List<NewsItem> items, Instant asOf, Duration window, String value) {
        Instant start = asOf.minus(window);
        long matches = items.stream().filter(item -> inWindow(item, start, asOf))
                .filter(item -> value.equalsIgnoreCase(text(item.category())) || value.equalsIgnoreCase(text(item.eventType())))
                .count();
        return matches == 0 ? null : Math.min(1.0, (double) matches);
    }

    private static Double event(List<NewsItem> items, Instant asOf, Duration window, String value) {
        Instant start = asOf.minus(window);
        return items.stream().anyMatch(item -> inWindow(item, start, asOf)
                && value.equalsIgnoreCase(text(item.eventType()))) ? 1.0 : 0.0;
    }

    private static boolean category(NewsItem item, String value) {
        return value.equalsIgnoreCase(text(item.category()));
    }

    private static boolean inWindow(NewsItem item, Instant start, Instant end) {
        return !item.publicationTimestamp().isBefore(start) && !item.publicationTimestamp().isAfter(end);
    }

    private static double relevance(NewsItem item) {
        return item.relevance() == null || !Double.isFinite(item.relevance()) ? 1.0 : Math.max(0.0, item.relevance());
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}