package com.tradingplatform.market.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.MarketDataUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpstoxOvernightInstrumentResolverTest {

    @Test
    void resolvesNiftyGlobalIndexesFromTheLocalGlobalInstrumentMaster(@TempDir Path directory) throws Exception {
        Path globalMaster = directory.resolve("global.json.gz");
        String rows = """
                [
                  {"name":"GIFT NIFTY","segment":"GLOBAL_INDEX","exchange":"GLOBAL",
                   "instrument_key":"GLOBAL_INDEX|SGX NIFTY","trading_symbol":"GIFT NIFTY"},
                  {"name":"S&P","segment":"GLOBAL_INDEX","exchange":"GLOBAL",
                   "instrument_key":"GLOBAL_INDEX|^GSPC","trading_symbol":"^GSPC"},
                  {"name":"US Tech 100","segment":"GLOBAL_INDEX","exchange":"GLOBAL",
                   "instrument_key":"GLOBAL_INDEX|IXIX","trading_symbol":"IXIX"},
                  {"name":"DOW JONES","segment":"GLOBAL_INDEX","exchange":"GLOBAL",
                   "instrument_key":"GLOBAL_INDEX|^DJI","trading_symbol":"^DJI"},
                  {"name":"NIKKEI 225","segment":"GLOBAL_INDEX","exchange":"GLOBAL",
                   "instrument_key":"GLOBAL_INDEX|^N225","trading_symbol":"^N225"},
                  {"name":"HANG SENG","segment":"GLOBAL_INDEX","exchange":"GLOBAL",
                   "instrument_key":"GLOBAL_INDEX|^HSI","trading_symbol":"^HSI"},
                  {"name":"FTSE 100","segment":"GLOBAL_INDEX","exchange":"GLOBAL",
                   "instrument_key":"GLOBAL_INDEX|^FTSE","trading_symbol":"^FTSE"},
                  {"name":"DAX","segment":"GLOBAL_INDEX","exchange":"GLOBAL",
                   "instrument_key":"GLOBAL_INDEX|^GDAXI","trading_symbol":"^GDAXI"},
                  {"name":"Oil (Brent)","segment":"GLOBAL_INDICATOR","exchange":"GLOBAL",
                   "instrument_key":"GLOBAL_INDICATOR|BZUSD","trading_symbol":"BZUSD"}
                ]
                """;
        writeGzip(globalMaster, rows);
        UpstoxOvernightInstrumentResolver resolver = new UpstoxOvernightInstrumentResolver(
                new ObjectMapper(), "", globalMaster.toString(), "");

        Map<String, String> expectedKeys = Map.of(
                "GIFT_NIFTY", "GLOBAL_INDEX|SGX NIFTY",
                "SP500", "GLOBAL_INDEX|^GSPC",
                "US_TECH_100", "GLOBAL_INDEX|IXIX",
                "DOW", "GLOBAL_INDEX|^DJI",
                "NIKKEI", "GLOBAL_INDEX|^N225",
                "HANG_SENG", "GLOBAL_INDEX|^HSI",
                "FTSE_100", "GLOBAL_INDEX|^FTSE",
                "DAX", "GLOBAL_INDEX|^GDAXI",
                "BRENT", "GLOBAL_INDICATOR|BZUSD");

        expectedKeys.forEach((code, expectedKey) -> assertEquals(expectedKey,
                resolver.resolve(code, "GLOBAL_INDEX|stale-config", "GLOBAL_INDEX").get(0).instrumentKey()));
        assertEquals("GLOBAL_INDICATOR",
                resolver.resolve("BRENT", "", "GLOBAL_INDICATOR").get(0).segment());
    }

    @Test
    void resolvesActiveFuturesFromTheLocalCompleteInstrumentMaster(@TempDir Path directory) throws Exception {
        Path completeMaster = directory.resolve("complete.json.gz");
        String rows = """
                [
                  {"name":"USDINR","segment":"NCD_FO","exchange":"NSE","instrument_type":"FUT",
                   "trading_symbol":"USDINR FUT NSE","instrument_key":"NCD_FO|local-usdinr","expiry":%d},
                  {"name":"USDINR","segment":"BCD_FO","exchange":"BSE","instrument_type":"FUT",
                   "trading_symbol":"USDINR FUT BSE","instrument_key":"BCD_FO|local-bse","expiry":%d},
                  {"name":"CRUDEOIL","segment":"NSE_COM","exchange":"NSE","instrument_type":"FUT",
                   "trading_symbol":"CRUDEOIL FUT","instrument_key":"NSE_COM|local-crude","expiry":%d},
                  {"name":"GOLD","segment":"NSE_COM","exchange":"NSE","instrument_type":"FUT",
                   "trading_symbol":"GOLD FUT 05 NOV 26","instrument_key":"NSE_COM|124394","expiry":%d}
                ]
                """.formatted(
                Instant.now().plusSeconds(3600).toEpochMilli(),
                Instant.now().plusSeconds(7200).toEpochMilli(),
                Instant.now().plusSeconds(3600).toEpochMilli(),
                Instant.now().plusSeconds(3600).toEpochMilli());
        writeGzip(completeMaster, rows);
        UpstoxOvernightInstrumentResolver resolver = new UpstoxOvernightInstrumentResolver(
                new ObjectMapper(), "http://127.0.0.1:1/unused.json.gz", "", completeMaster.toString());

        assertEquals("NCD_FO|local-usdinr",
                resolver.resolve("USD_INR", "", "GLOBAL_INDICATOR").get(0).instrumentKey());
        assertEquals("NSE_COM|local-crude",
                resolver.resolve("CRUDE_OIL", "", "GLOBAL_INDICATOR").get(0).instrumentKey());
        assertEquals("NSE_COM|124394",
                resolver.resolve("GOLD", "", "GLOBAL_INDICATOR").get(0).instrumentKey());
    }

    @Test
    void refreshesFromUpstoxWhenLocalMasterHasNoActiveContract(@TempDir Path directory) throws Exception {
        Path completeMaster = directory.resolve("complete.json.gz");
        writeGzip(completeMaster, """
                [{"name":"CRUDEOIL","segment":"NSE_COM","exchange":"NSE","instrument_type":"FUT",
                  "trading_symbol":"CRUDEOIL FUT EXPIRED","instrument_key":"NSE_COM|expired","expiry":%d}]
                """.formatted(Instant.now().minusSeconds(3600).toEpochMilli()));
        String currentMaster = """
                [{"name":"CRUDEOIL","segment":"NSE_COM","exchange":"NSE","instrument_type":"FUT",
                  "trading_symbol":"CRUDEOIL FUT CURRENT","instrument_key":"NSE_COM|current","expiry":%d}]
                """.formatted(Instant.now().plusSeconds(3600).toEpochMilli());
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/master.json", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            byte[] response = currentMaster.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            UpstoxOvernightInstrumentResolver resolver = new UpstoxOvernightInstrumentResolver(
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/master.json",
                    "",
                    completeMaster.toString());

            ResolvedOvernightInstrument resolved =
                    resolver.resolve("CRUDE_OIL", "", "GLOBAL_INDICATOR").get(0);

            assertEquals("NSE_COM|current", resolved.instrumentKey());
            assertEquals("/master.json", requestPath.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolvesNearestUnexpiredUsdInrFutureAndIgnoresLegacyInvalidKey() throws Exception {
        String rows = """
                [
                  {"name":"USDINR","segment":"NCD_FO","exchange":"NSE","instrument_type":"FUT",
                   "trading_symbol":"USDINR FUT NEAR","instrument_key":"NCD_FO|near","expiry":%d},
                  {"name":"USDINR","segment":"NCD_FO","exchange":"NSE","instrument_type":"FUT",
                   "trading_symbol":"USDINR FUT FAR","instrument_key":"NCD_FO|far","expiry":%d},
                  {"name":"USDINR","segment":"BCD_FO","exchange":"BSE","instrument_type":"FUT",
                   "trading_symbol":"USDINR FUT BSE","instrument_key":"BCD_FO|bse","expiry":%d},
                  {"name":"USDINR","segment":"NCD_FO","exchange":"NSE","instrument_type":"CE",
                   "trading_symbol":"USDINR CE","instrument_key":"NCD_FO|option","expiry":%d},
                  {"name":"CRUDEOIL","segment":"NSE_COM","exchange":"NSE","instrument_type":"FUT",
                   "trading_symbol":"CRUDEOIL FUT","instrument_key":"NSE_COM|crude","expiry":%d}
                ]
                """.formatted(
                Instant.now().plusSeconds(3600).toEpochMilli(),
                Instant.now().plusSeconds(7200).toEpochMilli(),
                Instant.now().plusSeconds(1800).toEpochMilli(),
                Instant.now().plusSeconds(1800).toEpochMilli(),
                Instant.now().plusSeconds(3600).toEpochMilli());

        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            byte[] response = rows.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            UpstoxOvernightInstrumentResolver resolver = new UpstoxOvernightInstrumentResolver(
                    new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort() + "/master.json");

            var candidates = resolver.resolve("USD_INR", "GLOBAL_INDICATOR|USDINR", "GLOBAL_INDICATOR");
            ResolvedOvernightInstrument resolved = candidates.get(0);

            assertEquals("NCD_FO|near", resolved.instrumentKey());
            assertEquals("NSE", resolved.exchange());
            assertEquals("NCD_FO", resolved.segment());
            assertEquals(3, candidates.size());
            assertEquals("BCD_FO|bse", candidates.get(2).instrumentKey());
            assertEquals("BSE", candidates.get(2).exchange());
            assertEquals("/master.json", requestPath.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolvesNseCrudeOilFutureForTheClearlyLabeledProxy() throws Exception {
        String rows = """
                [{"name":"CRUDEOIL","segment":"NSE_COM","exchange":"NSE","instrument_type":"FUT",
                  "trading_symbol":"CRUDEOIL FUT","instrument_key":"NSE_COM|crude","expiry":%d}]
                """.formatted(Instant.now().plusSeconds(3600).toEpochMilli());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] response = rows.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            UpstoxOvernightInstrumentResolver resolver = new UpstoxOvernightInstrumentResolver(
                    new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort() + "/master.json");

            ResolvedOvernightInstrument resolved =
                    resolver.resolve("CRUDE_OIL", "", "GLOBAL_INDICATOR").get(0);

            assertEquals("NSE_COM|crude", resolved.instrumentKey());
            assertEquals("NSE_COM", resolved.segment());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsClearlyWhenTheInstrumentMasterHasNoEligibleContract() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] response = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            UpstoxOvernightInstrumentResolver resolver = new UpstoxOvernightInstrumentResolver(
                    new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort() + "/master.json");
            assertThrows(MarketDataUnavailableException.class,
                    () -> resolver.resolve("CRUDE_OIL", "", "GLOBAL_INDICATOR"));
        } finally {
            server.stop(0);
        }
    }

    private static void writeGzip(Path path, String content) throws Exception {
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
