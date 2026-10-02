package com.tradingplatform.market.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Marketaux implementation of the platform's single news-provider boundary. */
@Component
@ConditionalOnExpression("'${trading.news.provider:marketaux}' == 'marketaux' and '${trading.news.marketaux.api-key:}' != ''")
public class MarketauxNewsProvider implements NewsProvider {
    private static final String NEWS_PATH = "/v1/news/all";

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String country;
    private final String language;
    private final int limit;

    @Autowired
    public MarketauxNewsProvider(ObjectMapper objectMapper,
                                 @Value("${trading.news.marketaux.base-url:https://api.marketaux.com}") String baseUrl,
                                 @Value("${trading.news.marketaux.api-key:}") String apiKey,
                                 @Value("${trading.news.marketaux.country:in}") String country,
                                 @Value("${trading.news.marketaux.language:en}") String language,
                                 @Value("${trading.news.marketaux.limit:3}") int limit) {
        this(HttpClient.newHttpClient(), objectMapper, baseUrl, apiKey, country, language, limit);
    }

    MarketauxNewsProvider(HttpClient client, ObjectMapper objectMapper, String baseUrl, String apiKey,
                          String country, String language, int limit) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.baseUrl = requireText(baseUrl, "Marketaux base URL");
        this.apiKey = requireText(apiKey, "Marketaux API key (set NEWS_API_KEY)");
        this.country = requireText(country, "Marketaux country");
        this.language = requireText(language, "Marketaux language");
        if (limit < 1) {
            throw new IllegalArgumentException("Marketaux limit must be at least 1.");
        }
        this.limit = limit;
    }

    @Override
    public List<NewsItem> fetch(Instant asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("News timestamp cannot be null.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(requestUrl())).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Marketaux request failed with HTTP " + response.statusCode() + ".");
            }
            return mapResponse(response.body(), asOf, Instant.now());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Marketaux request was interrupted.", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to retrieve or parse Marketaux news response.", ex);
        }
    }

    private String requestUrl() {
        return baseUrl.replaceAll("/+$", "") + NEWS_PATH
                + "?countries=" + encode(country)
                + "&language=" + encode(language)
                + "&limit=" + limit
                + "&api_token=" + encode(apiKey);
    }

    List<NewsItem> mapResponse(String body, Instant asOf, Instant ingestionTime) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode articles = root.path("data");
            if (!articles.isArray()) {
                throw new IllegalStateException("Marketaux response does not contain an article data array.");
            }
            List<NewsItem> items = new ArrayList<>();
            for (JsonNode article : articles) {
                NewsItem item = mapArticle(article, ingestionTime);
                if (item != null && !item.publicationTimestamp().isAfter(asOf)) {
                    items.add(item);
                }
            }
            return List.copyOf(items);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse Marketaux news response.", ex);
        }
    }

    private NewsItem mapArticle(JsonNode article, Instant ingestionTime) {
        String publishedAt = text(article, "published_at");
        if (publishedAt == null) {
            return null;
        }
        final Instant publicationTime;
        try {
            publicationTime = Instant.parse(publishedAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Marketaux article has an invalid published_at timestamp.", ex);
        }
        List<JsonNode> entities = array(article.path("entities"));
        List<String> instruments = entities.stream().map(entity -> text(entity, "symbol"))
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        Double sentiment = averageFinite(entities, "sentiment_score");
        String articleText = String.join(" ", nonNull(text(article, "title"), text(article, "description"),
                text(article, "keywords"), text(article, "snippet"), text(article, "source")));
        return new NewsItem(text(article, "title"), text(article, "source"), text(article, "url"), publicationTime,
                ingestionTime, instruments, NewsClassifier.category(articleText, entities), sentiment,
                finiteNumber(article.get("relevance_score")), NewsClassifier.eventType(articleText));
    }

    private static List<JsonNode> array(JsonNode node) {
        List<JsonNode> values = new ArrayList<>();
        if (node.isArray()) node.forEach(values::add);
        return values;
    }

    private static Double averageFinite(List<JsonNode> entities, String field) {
        double total = 0.0;
        int count = 0;
        for (JsonNode entity : entities) {
            Double score = finiteNumber(entity.get(field));
            if (score != null) {
                total += score.doubleValue();
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }

    private static Double finiteNumber(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) return null;
        double value = node.asDouble();
        return Double.isFinite(value) ? value : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static List<String> nonNull(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value != null) result.add(value);
        return result;
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required.");
        return value;
    }

    static final class NewsClassifier {
        private NewsClassifier() { }

        static String category(String articleText, List<JsonNode> entities) {
            if (contains(articleText, "india", "indian", "rbi", "reserve bank of india", "nifty", "sensex")
                    || entities.stream().anyMatch(entity -> {
                        String country = text(entity, "country");
                        return "in".equalsIgnoreCase(country) || "india".equalsIgnoreCase(country);
                    })) return "india";
            return "global";
        }

        static String eventType(String articleText) {
            if (contains(articleText, "reserve bank of india", "rbi", "repo rate", "monetary policy", "mpc")) return "rbi";
            if (contains(articleText, "federal reserve", "fomc", "federal funds rate", " fed ")) return "fed";
            if (contains(articleText, "war", "conflict", "sanctions", "geopolitical", "invasion", "military conflict", "trade war")) return "geopolitics";
            if (contains(articleText, "inflation", "cpi", "gdp", "unemployment", "jobs", "pmi", "interest rate", "central bank", "monetary policy", "economic growth")) return "macro";
            return null;
        }

        private static boolean contains(String text, String... phrases) {
            String normalized = " " + (text == null ? "" : text.toLowerCase(Locale.ROOT)) + " ";
            for (String phrase : phrases) if (normalized.contains(phrase.toLowerCase(Locale.ROOT))) return true;
            return false;
        }
    }
}
