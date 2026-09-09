package com.tradingplatform.tools;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TwelveDataTester {

    private static final String BASE_URL =
            "https://api.twelvedata.com";

    private static final List<String> SYMBOLS = List.of(
            "SPX",
            "NDX",
            "DJI",
            "VIX",
            "USD/INR",
            "XAU/USD",
            "XBR/USD",
            "TNX",
            "NIFTY",
            "NIKKEI",
            "HSI",
            "DAX"
    );

    public static void main(String[] args) throws Exception {

        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        String apiKey = dotenv.get("TWELVE_DATA_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println(
                    "ERROR: TWELVE_DATA_API_KEY not found in .env"
            );
            System.exit(1);
        }

        HttpClient client = HttpClient.newHttpClient();

        System.out.println();
        System.out.println("==================================================");
        System.out.println("        TWELVE DATA BASIC PLAN TEST");
        System.out.println("==================================================");

        for (String symbol : SYMBOLS) {
            testSymbol(client, symbol, apiKey);
        }

        System.out.println();
        System.out.println("==================================================");
        System.out.println("                    TEST COMPLETE");
        System.out.println("==================================================");
    }

    private static void testSymbol(
            HttpClient client,
            String symbol,
            String apiKey
    ) throws Exception {

        String encodedSymbol =
                URLEncoder.encode(symbol, StandardCharsets.UTF_8);

        String url = BASE_URL
                + "/time_series"
                + "?symbol=" + encodedSymbol
                + "&interval=1day"
                + "&outputsize=3"
                + "&apikey=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        System.out.println();
        System.out.println("-----------------------------------------------");
        System.out.println("Instrument : " + symbol);
        System.out.println("HTTP       : " + response.statusCode());

        String body = response.body();

        // Never print the API key.
        if (body.contains("\"status\":\"error\"")
                || body.contains("\"code\"")) {

            System.out.println("Result     : ❌ FAILED");
            System.out.println("Response   : " + body);

        } else {

            System.out.println("Result     : ✅ RESPONSE RECEIVED");
            System.out.println("Response   : " + body);
        }
    }
}