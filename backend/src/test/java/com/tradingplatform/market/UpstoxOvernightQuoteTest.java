package com.tradingplatform.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UpstoxOvernightQuoteTest {

    private static final String GIFT_KEY = "GLOBAL_INDEX|SGX NIFTY";
    private static final Instrument GIFT_NIFTY =
            new Instrument(GIFT_KEY, "GIFT_NIFTY", "Gift Nifty", "GLOBAL", "GLOBAL_INDEX", "", true);

    @Test
    void requestsExactQuotesEndpointAndParsesTheConfirmedResponseShape() throws Exception {
        AtomicReference<String> requestUri = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = server(response(GIFT_KEY, "22824.5", "50.5",
                "2026-10-06T16:57:52.687+05:30"), requestUri, authorization);
        try {
            Quote quote = provider(server).getOvernightQuote(GIFT_NIFTY);

            assertEquals("/v3/market-quote/quotes?instrument_key=GLOBAL_INDEX%7CSGX%20NIFTY",
                    requestUri.get());
            assertTrue(authorization.get() != null && authorization.get().startsWith("Bearer "),
                    "Provider request must include bearer authentication");
            assertEquals("22824.5", quote.price().toPlainString());
            assertEquals("50.5", quote.change().toPlainString());
            assertEquals(Instant.parse("2026-10-06T11:27:52.687Z"), quote.timestamp());
            assertNull(quote.previousClose());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void independentlyParsesGiftNiftySp500AndUsTech100() throws Exception {
        List<Instrument> instruments = List.of(
                GIFT_NIFTY,
                new Instrument("GLOBAL_INDEX|^GSPC", "SP500", "S&P 500",
                        "GLOBAL", "GLOBAL_INDEX", "", true),
                new Instrument("GLOBAL_INDEX|IXIX", "US_TECH_100", "US Tech 100",
                        "GLOBAL", "GLOBAL_INDEX", "", true)
        );
        List<String> prices = List.of("22824.5", "7791.55", "27482.32");
        List<String> changes = List.of("50.5", "47.83", "270.45");
        List<String> timestamps = List.of(
                "2026-10-06T16:57:52.687+05:30",
                "2026-10-06T16:59:14.033+05:30",
                "2026-10-06T17:00:49.723+05:30"
        );

        for (int index = 0; index < instruments.size(); index++) {
            Instrument instrument = instruments.get(index);
            AtomicReference<String> requestUri = new AtomicReference<>();
            HttpServer server = server(response(instrument.instrumentKey(), prices.get(index),
                    changes.get(index), timestamps.get(index)), requestUri, new AtomicReference<>());
            try {
                Quote quote = provider(server).getOvernightQuote(instrument);
                String encodedKey = java.net.URLEncoder.encode(
                        instrument.instrumentKey(), StandardCharsets.UTF_8).replace("+", "%20");
                assertEquals("/v3/market-quote/quotes?instrument_key=" + encodedKey, requestUri.get());
                assertEquals(prices.get(index), quote.price().toPlainString());
                assertEquals(changes.get(index), quote.change().toPlainString());
                assertEquals(Instant.parse(timestamps.get(index)), quote.timestamp());
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void rejectsQuoteWithoutProviderTimestampInsteadOfSubstitutingCurrentTime() throws Exception {
        AtomicReference<String> requestUri = new AtomicReference<>();
        HttpServer server = server("""
                {"data":{"GLOBAL_INDEX|SGX NIFTY":{
                  "last_price":22824.5,"net_change":50.5,
                  "ohlc":{"open":22649.0,"high":22825.0,"low":22616.0}
                }}}
                """, requestUri, new AtomicReference<>());
        try {
            assertThrows(MarketDataUnavailableException.class,
                    () -> provider(server).getOvernightQuote(GIFT_NIFTY));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retrievesGlobalIndicatorLtpFromV2EndpointWithoutInventingChangeOrTimestamp() throws Exception {
        Instrument brent = new Instrument("GLOBAL_INDICATOR|BZUSD", "BRENT", "Brent crude",
                "GLOBAL", "GLOBAL_INDICATOR", "", true);
        AtomicReference<String> requestUri = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = server("""
                {"status":"success","data":{"GLOBAL_INDICATOR:null":{
                  "instrument_token":"GLOBAL_INDICATOR|BZUSD","last_price":78.45
                }}}
                """, requestUri, authorization);
        try {
            Quote quote = provider(server).getOvernightQuote(brent);

            assertEquals("/v2/market-quote/ltp?instrument_key=GLOBAL_INDICATOR%7CBZUSD",
                    requestUri.get());
            assertTrue(authorization.get() != null && authorization.get().startsWith("Bearer "));
            assertEquals("78.45", quote.price().toPlainString());
            assertNull(quote.change());
            assertNull(quote.timestamp());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToGlobalSymbolWhenUpstoxRejectsBrentInstrumentKey() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> fallbackUri = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/market-quote/ltp", exchange -> {
            int requestNumber = requestCount.incrementAndGet();
            byte[] body;
            int status;
            if (requestNumber == 1) {
                body = """
                        {"status":"error","errors":[{"errorCode":"UDAPI1087",
                         "message":"One of either symbol or instrument_key is invalid."}]}
                        """.getBytes(StandardCharsets.UTF_8);
                status = 400;
            } else {
                fallbackUri.set(exchange.getRequestURI().toString());
                body = """
                        {"data":{"BZUSD":{"instrument_token":"GLOBAL_INDICATOR|BZUSD",
                         "last_price":79.25}}}
                        """.getBytes(StandardCharsets.UTF_8);
                status = 200;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Instrument brent = new Instrument("GLOBAL_INDICATOR|BZUSD", "BRENT", "Brent crude",
                    "GLOBAL", "GLOBAL_INDICATOR", "", true);

            Quote quote = provider(server).getOvernightQuote(brent);

            assertEquals(2, requestCount.get());
            assertEquals("/v2/market-quote/ltp?symbol=BZUSD", fallbackUri.get());
            assertEquals("79.25", quote.price().toPlainString());
            assertNull(quote.timestamp());
            assertNull(quote.change());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsDomesticFuturePriceWithoutInventingMissingChangeOrTimestamp() throws Exception {
        String key = "NSE_COM|gold-future";
        AtomicReference<String> requestUri = new AtomicReference<>();
        HttpServer server = server("""
                {"data":{"NSE_COM|gold-future":{
                  "last_price":71450.0,"net_change":null,
                  "ohlc":{"timestamp":"not-a-timestamp"},
                  "last_trade_time":null
                }}}
                """, requestUri, new AtomicReference<>());
        try {
            Instrument gold = new Instrument(key, "GOLD", "NSE Gold futures",
                    "NSE", "NSE_COM", "", true);

            Quote quote = provider(server).getOvernightQuote(gold);

            assertEquals("/v3/market-quote/quotes?instrument_key=NSE_COM%7Cgold-future",
                    requestUri.get());
            assertEquals("71450.0", quote.price().toPlainString());
            assertNull(quote.change());
            assertNull(quote.timestamp());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skipsMalformedOhlcTimestampWhenAValidQuoteTimestampIsPresent() throws Exception {
        String key = "NCD_FO|usdinr-future";
        AtomicReference<String> requestUri = new AtomicReference<>();
        HttpServer server = server("""
                {"data":{"NCD_FO|usdinr-future":{
                  "last_price":83.25,"net_change":0.15,
                  "timestamp":"2026-10-08T15:40:00+05:30",
                  "ohlc":{"timestamp":"not-a-timestamp"}
                }}}
                """, requestUri, new AtomicReference<>());
        try {
            Instrument usdInr = new Instrument(key, "USD_INR", "USD/INR futures",
                    "NSE", "NCD_FO", "", true);

            Quote quote = provider(server).getOvernightQuote(usdInr);

            assertEquals("83.25", quote.price().toPlainString());
            assertEquals("0.15", quote.change().toPlainString());
            assertEquals(Instant.parse("2026-10-08T10:10:00Z"), quote.timestamp());
        } finally {
            server.stop(0);
        }
    }

    private static UpstoxMarketDataProvider provider(HttpServer server) {
        return new UpstoxMarketDataProvider(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-token");
    }

    private static HttpServer server(
            String responseBody,
            AtomicReference<String> requestUri,
            AtomicReference<String> authorization
    ) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/market-quote/quotes", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v2/market-quote/ltp", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String response(String instrumentKey, String price, String change, String timestamp) {
        return """
                {"data":{"%s":{
                  "last_price":%s,"net_change":%s,"instrument_token":"%s",
                  "timestamp":"%s",
                  "ohlc":{"open":22649.0,"high":22825.0,"low":22616.0}
                }}}
                """.formatted(instrumentKey, price, change, instrumentKey, timestamp);
    }
}
