package com.tradingplatform.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.nse.NseMarketDataService;
import com.tradingplatform.market.nse.NseReferenceSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NseReferenceEndpointTest {
    private static final String NSE_BASE_URL = "https://www.nseindia.com";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void indiaVixRouteUsesAllIndicesAndReturnsOnlyTheValidatedVixRow() throws Exception {
        RestClient.Builder clientBuilder = RestClient.builder().baseUrl(NSE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        NseReferenceSnapshotService snapshots = mock(NseReferenceSnapshotService.class);
        JsonNode vix = mapper.readTree("""
                {"index":"INDIA VIX","last":13.59,"variation":-1.19}
                """);
        when(snapshots.persistIndiaVix(any(JsonNode.class))).thenReturn(vix);
        server.expect(requestTo(NSE_BASE_URL + "/"))
                .andRespond(withSuccess());
        server.expect(requestTo(NSE_BASE_URL + "/api/allIndices"))
                .andRespond(withSuccess("""
                        {"data":[{"index":"NIFTY 50"},{"index":"INDIA VIX","last":13.59,"variation":-1.19}]}
                        """, MediaType.APPLICATION_JSON));

        NseMarketDataController controller =
                new NseMarketDataController(new NseMarketDataService(clientBuilder.build()), snapshots);

        JsonNode response = controller.indexData("INDIA VIX");

        assertEquals(1, response.path("data").size());
        assertEquals("INDIA VIX", response.path("data").get(0).path("index").asText());
        verify(snapshots).persistIndiaVix(any(JsonNode.class));
        server.verify();
    }

    @Test
    void fiiDiiRouteUsesTheVerifiedProviderEndpointAndPersistsTheResponse() throws Exception {
        RestClient.Builder clientBuilder = RestClient.builder().baseUrl(NSE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        NseReferenceSnapshotService snapshots = mock(NseReferenceSnapshotService.class);
        server.expect(requestTo(NSE_BASE_URL + "/"))
                .andRespond(withSuccess());
        server.expect(requestTo(NSE_BASE_URL + "/api/fiidiiTradeReact"))
                .andRespond(withSuccess("""
                        {"value":[
                          {"category":"DII","date":"06-Oct-2026","buyValue":"1","sellValue":"2","netValue":"-1"},
                          {"category":"FII/FPI","date":"06-Oct-2026","buyValue":"3","sellValue":"4","netValue":"-1"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        NseMarketDataController controller =
                new NseMarketDataController(new NseMarketDataService(clientBuilder.build()), snapshots);

        JsonNode response = controller.fiiDii();

        assertEquals(2, response.path("value").size());
        verify(snapshots).persistFiiDii(response);
        server.verify();
    }

    @Test
    void fiiDiiRouteNormalizesAnArrayResponseFromNse() throws Exception {
        RestClient.Builder clientBuilder = RestClient.builder().baseUrl(NSE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        NseReferenceSnapshotService snapshots = mock(NseReferenceSnapshotService.class);
        server.expect(requestTo(NSE_BASE_URL + "/"))
                .andRespond(withSuccess());
        server.expect(requestTo(NSE_BASE_URL + "/api/fiidiiTradeReact"))
                .andRespond(withSuccess("""
                        [
                          {"category":"DII","date":"06-Oct-2026","buyValue":"1","sellValue":"2","netValue":"-1"},
                          {"category":"FII/FPI","date":"06-Oct-2026","buyValue":"3","sellValue":"4","netValue":"-1"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        NseMarketDataController controller =
                new NseMarketDataController(new NseMarketDataService(clientBuilder.build()), snapshots);

        JsonNode response = controller.fiiDii();

        assertEquals(2, response.path("value").size());
        verify(snapshots).persistFiiDii(response);
        server.verify();
    }

    @Test
    void premarketRouteCombinesValidatedIndiaVixAndFiiDiiSources() throws Exception {
        RestClient.Builder clientBuilder = RestClient.builder().baseUrl(NSE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        NseReferenceSnapshotService snapshots = mock(NseReferenceSnapshotService.class);
        JsonNode vix = mapper.readTree("""
                {"index":"INDIA VIX","last":13.59,"variation":-1.19,"percentChange":-8.03,
                 "open":14.78,"high":14.78,"low":13.52,"previousClose":14.78}
                """);
        JsonNode normalizedVix = mapper.readTree("""
                {"index":"INDIA VIX","symbol":"INDIA VIX","last":13.59,"variation":-1.19,
                 "percentChange":-8.03,"open":14.78,"high":14.78,"low":13.52,
                 "previousClose":14.78,"change":-1.19}
                """);
        JsonNode flows = mapper.readTree("""
                {"value":[
                  {"category":"DII","date":"06-Oct-2026","buyValue":"1","sellValue":"2","netValue":"-1"},
                  {"category":"FII/FPI","date":"06-Oct-2026","buyValue":"3","sellValue":"4","netValue":"-1"}
                ]}
                """);
        when(snapshots.persistIndiaVix(any(JsonNode.class))).thenReturn(normalizedVix);
        server.expect(requestTo(NSE_BASE_URL + "/"))
                .andRespond(withSuccess());
        server.expect(requestTo(NSE_BASE_URL + "/api/allIndices"))
                .andRespond(withSuccess("""
                        {"data":[{"index":"NIFTY 50"},{"index":"INDIA VIX","last":13.59,
                         "variation":-1.19,"percentChange":-8.03,"open":14.78,"high":14.78,
                         "low":13.52,"previousClose":14.78}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NSE_BASE_URL + "/api/fiidiiTradeReact"))
                .andRespond(withSuccess("""
                        {"value":[
                          {"category":"DII","date":"06-Oct-2026","buyValue":"1","sellValue":"2","netValue":"-1"},
                          {"category":"FII/FPI","date":"06-Oct-2026","buyValue":"3","sellValue":"4","netValue":"-1"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        NseMarketDataController controller =
                new NseMarketDataController(new NseMarketDataService(clientBuilder.build()), snapshots);

        JsonNode response = controller.premarket();

        assertEquals("INDIA VIX", response.path("indiaVix").path("symbol").asText());
        assertEquals(2, response.path("fiiDii").path("value").size());
        verify(snapshots).persistIndiaVix(any(JsonNode.class));
        verify(snapshots).persistFiiDii(any(JsonNode.class));
        server.verify();
    }

    @Test
    void premarketRouteKeepsIndiaVixWhenFiiDiiProviderIsUnavailable() throws Exception {
        RestClient.Builder clientBuilder = RestClient.builder().baseUrl(NSE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        NseReferenceSnapshotService snapshots = mock(NseReferenceSnapshotService.class);
        JsonNode normalizedVix = mapper.readTree("""
                {"index":"INDIA VIX","symbol":"INDIA VIX","last":13.59,"variation":-1.19,
                 "percentChange":-8.03,"open":14.78,"high":14.78,"low":13.52,
                 "previousClose":14.78,"change":-1.19}
                """);
        when(snapshots.persistIndiaVix(any(JsonNode.class))).thenReturn(normalizedVix);
        server.expect(requestTo(NSE_BASE_URL + "/"))
                .andRespond(withSuccess());
        server.expect(requestTo(NSE_BASE_URL + "/api/allIndices"))
                .andRespond(withSuccess("""
                        {"data":[{"index":"INDIA VIX","last":13.59,"variation":-1.19,
                         "percentChange":-8.03,"open":14.78,"high":14.78,"low":13.52,
                         "previousClose":14.78}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.twice(), requestTo(NSE_BASE_URL + "/api/fiidiiTradeReact"))
                .andRespond(withServerError());

        NseMarketDataController controller =
                new NseMarketDataController(new NseMarketDataService(clientBuilder.build()), snapshots);

        JsonNode response = controller.premarket();

        assertEquals("INDIA VIX", response.path("indiaVix").path("symbol").asText());
        assertTrue(response.path("fiiDiiError").asText().contains("HTTP 500"));
        assertTrue(response.path("fiiDii").isMissingNode());
        verify(snapshots, never()).persistFiiDii(any(JsonNode.class));
        server.verify();
    }
}
