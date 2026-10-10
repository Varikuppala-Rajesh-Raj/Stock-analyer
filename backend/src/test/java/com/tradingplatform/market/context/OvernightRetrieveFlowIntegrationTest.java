package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tradingplatform.controller.OvernightMarketController;
import com.tradingplatform.market.UpstoxMarketDataProvider;
import com.tradingplatform.persistence.OvernightMarketDataEntity;
import com.tradingplatform.persistence.OvernightMarketDataRepository;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OvernightRetrieveFlowIntegrationTest {

    private static final UpstoxGlobalInstrumentProperties INSTRUMENTS =
            new UpstoxGlobalInstrumentProperties(
                    "GLOBAL_INDEX|SGX NIFTY",
                    "GLOBAL_INDEX|^GSPC",
                    "GLOBAL_INDEX|IXIX",
                    "GLOBAL_INDEX|^DJI",
                    "GLOBAL_INDEX|^N225",
                    "GLOBAL_INDEX|^HSI",
                    "",
                    "",
                    "",
                    ""
            );
    private static final Map<String, String> PRICES = Map.of(
            "GLOBAL_INDEX|SGX NIFTY", "22824.5",
            "GLOBAL_INDEX|^GSPC", "7791.55",
            "GLOBAL_INDEX|IXIX", "27482.32"
    );
    private static final Map<String, String> CHANGES = Map.of(
            "GLOBAL_INDEX|SGX NIFTY", "50.5",
            "GLOBAL_INDEX|^GSPC", "47.83",
            "GLOBAL_INDEX|IXIX", "270.45"
    );

    @Test
    void retrievesAndPersistsEachConfirmedIndexThroughTheProviderAndService() throws Exception {
        AtomicReference<String> lastRequestUri = new AtomicReference<>();
        AtomicLong insertedId = new AtomicLong();
        OvernightMarketDataRepository repository = mock(OvernightMarketDataRepository.class);
        when(repository.findFirstByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
                anyString())).thenReturn(Optional.empty());
        when(repository.save(any(OvernightMarketDataEntity.class))).thenAnswer(invocation -> {
            OvernightMarketDataEntity entity = invocation.getArgument(0);
            entity.id = insertedId.incrementAndGet();
            return entity;
        });

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/market-quote/quotes", exchange -> {
            lastRequestUri.set(exchange.getRequestURI().toString());
            String query = exchange.getRequestURI().getRawQuery();
            String key = URLDecoder.decode(query.substring("instrument_key=".length()),
                    StandardCharsets.UTF_8);
            String body = """
                    {"data":{"%s":{
                      "last_price":%s,"net_change":%s,"instrument_token":"%s",
                      "timestamp":"%s",
                      "ohlc":{"open":22649.0,"high":22825.0,"low":22616.0}
                    }}}
                    """.formatted(key, PRICES.get(key), CHANGES.get(key), key, Instant.now().toString());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        try {
            UpstoxMarketDataProvider provider = new UpstoxMarketDataProvider(
                    new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(), "test-token");
            OvernightInstrumentResolver resolver = (instrument, configuredKey, fallbackSegment) ->
                    List.of(new ResolvedOvernightInstrument(
                            configuredKey, "GLOBAL", fallbackSegment, null));
            OvernightGlobalMarketService service =
                    new OvernightGlobalMarketService(provider, resolver, INSTRUMENTS, repository,
                            300, 900, 5, false);
            MockMvc api = MockMvcBuilders.standaloneSetup(new OvernightMarketController(service)).build();

            List<String> instrumentNames = List.of("GIFT_NIFTY", "SP500", "US_TECH_100");
            List<String> instrumentKeys = List.of(
                    INSTRUMENTS.giftNifty(), INSTRUMENTS.sp500(), INSTRUMENTS.usTech100());
            List<String> prices = List.of("22824.5", "7791.55", "27482.32");
            List<String> changes = List.of("50.5", "47.83", "270.45");
            for (int index = 0; index < instrumentNames.size(); index++) {
                api.perform(post("/api/overnight/retrieve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"instrument\":\"" + instrumentNames.get(index) + "\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.results[0].instrument").value(instrumentNames.get(index)))
                        .andExpect(jsonPath("$.results[0].instrumentKey").value(instrumentKeys.get(index)))
                        .andExpect(jsonPath("$.results[0].source").value("UPSTOX"))
                        .andExpect(jsonPath("$.results[0].status").value("FRESH"))
                        .andExpect(jsonPath("$.results[0].lastPrice").value(Double.parseDouble(prices.get(index))))
                        .andExpect(jsonPath("$.results[0].netChange").value(Double.parseDouble(changes.get(index))));
                assertEquals("/v3/market-quote/quotes?instrument_key="
                        + java.net.URLEncoder.encode(instrumentKeys.get(index), StandardCharsets.UTF_8)
                                .replace("+", "%20"), lastRequestUri.get());
                assertEquals(index + 1, insertedId.get());
            }
        } finally {
            server.stop(0);
        }
    }
}
