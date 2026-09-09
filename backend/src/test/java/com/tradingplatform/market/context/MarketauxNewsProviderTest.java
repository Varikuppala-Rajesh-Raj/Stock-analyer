package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketauxNewsProviderTest {
    private static final Instant AS_OF = Instant.parse("2024-01-01T10:00:00Z");
    private final MarketauxNewsProvider provider = new MarketauxNewsProvider(HttpClient.newHttpClient(), new ObjectMapper(),
            "https://api.marketaux.com", "test-key", "in", "en", 3);

    @Test
    void mapsMarketauxArticlesAndExcludesFutureArticles() {
        List<NewsItem> items = provider.mapResponse("""
                {"data":[
                  {"title":"RBI holds repo rate", "source":"Example News", "url":"https://example.test/rbi",
                   "published_at":"2024-01-01T09:55:00.000Z", "relevance_score":0.8,
                   "entities":[{"symbol":"NIFTY", "country":"in", "sentiment_score":0.4},
                               {"symbol":"NIFTY", "sentiment_score":0.8}, {"symbol":"", "sentiment_score":null}]},
                  {"title":"future item", "published_at":"2024-01-01T10:05:00Z", "entities":[]}
                ]}
                """, AS_OF, AS_OF.plusSeconds(1));

        assertEquals(1, items.size());
        NewsItem item = items.get(0);
        assertEquals("RBI holds repo rate", item.headline());
        assertEquals("Example News", item.source());
        assertEquals("https://example.test/rbi", item.url());
        assertEquals(Instant.parse("2024-01-01T09:55:00Z"), item.publicationTimestamp());
        assertEquals(List.of("NIFTY"), item.affectedInstruments());
        assertEquals(0.6, item.sentiment(), 0.00001);
        assertEquals(0.8, item.relevance());
        assertEquals("india", item.category());
        assertEquals("rbi", item.eventType());
    }

    @Test
    void classifiesIndiaGlobalAndEventTypesDeterministically() {
        assertEquals("india", MarketauxNewsProvider.NewsClassifier.category("India growth improves", List.of()));
        assertEquals("global", MarketauxNewsProvider.NewsClassifier.category("European shares rise", List.of()));
        assertEquals("rbi", MarketauxNewsProvider.NewsClassifier.eventType("RBI announces a repo rate decision"));
        assertEquals("fed", MarketauxNewsProvider.NewsClassifier.eventType("Federal Reserve signals patience"));
        assertEquals("geopolitics", MarketauxNewsProvider.NewsClassifier.eventType("New sanctions amid conflict"));
        assertEquals("macro", MarketauxNewsProvider.NewsClassifier.eventType("CPI inflation data surprises"));
    }

    @Test
    void preservesMissingOptionalFieldsAndRejectsMalformedTimestamp() {
        List<NewsItem> items = provider.mapResponse("""
                {"data":[{"title":"No scores", "published_at":"2024-01-01T09:59:00Z"},
                         {"title":"No timestamp"}]}
                """, AS_OF, AS_OF);
        assertEquals(1, items.size());
        assertNull(items.get(0).sentiment());
        assertNull(items.get(0).relevance());
        assertEquals(List.of(), items.get(0).affectedInstruments());
        assertThrows(IllegalStateException.class, () -> provider.mapResponse("""
                {"data":[{"published_at":"not-a-timestamp"}]}
                """, AS_OF, AS_OF));
    }
}
