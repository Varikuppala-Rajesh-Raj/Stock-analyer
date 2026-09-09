package com.tradingplatform.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

class UpstoxGlobalInstrumentIntegrationTest {

    private static UpstoxMarketDataProvider provider;

    private static final List<Instrument> GLOBAL_INSTRUMENTS = List.of(

        new Instrument(
            "GLOBAL_INDEX|^HSI",
            "HANG SENG",
            "Hang Seng Index",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDEX|^DJI",
            "DOW JONES",
            "Dow Jones Industrial Average",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDEX|^FTSE",
            "FTSE 100",
            "FTSE 100",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDICATOR|USDINR",
            "USD INR",
            "USD/INR",
            "GLOBAL",
            "GLOBAL_INDICATOR",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDICATOR|BZUSD",
            "BRENT",
            "Brent Crude Oil",
            "GLOBAL",
            "GLOBAL_INDICATOR",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDEX|^GSPC",
            "S&P 500",
            "S&P 500",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDEX|SGX NIFTY",
            "GIFT NIFTY",
            "GIFT NIFTY",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDEX|^GDAXI",
            "DAX",
            "DAX",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDEX|IXIX",
            "US TECH 100",
            "US Tech 100",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDEX|^FCHI",
            "CAC 40",
            "CAC 40",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDICATOR|CLUSD",
            "WTI",
            "WTI Crude Oil",
            "GLOBAL",
            "GLOBAL_INDICATOR",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDEX|DOW FUTURES",
            "US 30",
            "US 30",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        ),

        new Instrument(
            "GLOBAL_INDEX|^N225",
            "NIKKEI 225",
            "Nikkei 225",
            "GLOBAL",
            "GLOBAL_INDEX",
            "",
            true
        )
    );

    @BeforeAll
    static void setUp() {

        Dotenv dotenv = Dotenv.configure()
                .directory("..")
                .ignoreIfMissing()
                .load();

        String baseUrl = dotenv.get(
                "UPSTOX_BASE_URL",
                "https://api.upstox.com"
        ).trim();

        String token = dotenv.get("UPSTOX_ACCESS_TOKEN");

        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "UPSTOX_ACCESS_TOKEN was not found in ../.env"
            );
        }

        provider = new UpstoxMarketDataProvider(
                new ObjectMapper(),
                baseUrl,
                token.trim()
        );

        System.out.println();
        System.out.println("==============================================================");
        System.out.println("          UPSTOX GLOBAL INSTRUMENT INTEGRATION TEST");
        System.out.println("==============================================================");
        System.out.println("Base URL : " + baseUrl);
        System.out.println("Token    : configured");
        System.out.println("==============================================================");
    }

    @Test
    void testGlobalInstrumentQuotes() {

        System.out.println();
        System.out.println("--------------- QUOTE TEST ----------------");

        System.out.printf(
                "%-20s %-30s %-15s%n",
                "SYMBOL",
                "INSTRUMENT KEY",
                "RESULT"
        );

        System.out.println(
                "--------------------------------------------------------------------------"
        );

        for (Instrument instrument : GLOBAL_INSTRUMENTS) {

            try {

                Quote quote = provider.getQuote(instrument);

                boolean pass = quote != null && quote.price() != null;

                System.out.printf(
                        "%-20s %-30s %-15s%n",
                        instrument.symbol(),
                        instrument.instrumentKey(),
                        pass ? "PASS" : "FAIL"
                );

                if (pass) {
                    System.out.println(
                            "  Price : " + quote.price()
                    );

                    System.out.println(
                            "  Change: " + quote.change()
                                    + " (" + quote.changePercent() + "%)"
                    );
                }

            } catch (Exception e) {

                System.out.printf(
                        "%-20s %-30s %-15s%n",
                        instrument.symbol(),
                        instrument.instrumentKey(),
                        "FAIL"
                );

                System.out.println(
                        "  ERROR: " + e.getMessage()
                );
            }
        }
    }

    @Test
    void testGlobalInstrumentIntraday() {

        System.out.println();
        System.out.println("--------------- INTRADAY M5 TEST ----------------");

        System.out.printf(
                "%-20s %-30s %-10s %-10s%n",
                "SYMBOL",
                "INSTRUMENT KEY",
                "CANDLES",
                "RESULT"
        );

        System.out.println(
                "--------------------------------------------------------------------------"
        );

        for (Instrument instrument : GLOBAL_INSTRUMENTS) {

            try {

                List<Candle> candles =
                        provider.getIntradayCandles(
                                instrument.instrumentKey(),
                                Timeframe.M5
                        );

                int count = candles == null ? 0 : candles.size();

                System.out.printf(
                        "%-20s %-30s %-10d %-10s%n",
                        instrument.symbol(),
                        instrument.instrumentKey(),
                        count,
                        count > 0 ? "PASS" : "FAIL"
                );

            } catch (Exception e) {

                System.out.printf(
                        "%-20s %-30s %-10s %-10s%n",
                        instrument.symbol(),
                        instrument.instrumentKey(),
                        "-",
                        "FAIL"
                );

                System.out.println(
                        "  ERROR: " + e.getMessage()
                );
            }
        }
    }

    @Test
    void testGlobalInstrumentHistorical() {

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(1);

        System.out.println();
        System.out.println("--------------- HISTORICAL 1 MONTH TEST ----------------");

        System.out.printf(
                "%-20s %-30s %-10s %-10s%n",
                "SYMBOL",
                "INSTRUMENT KEY",
                "CANDLES",
                "RESULT"
        );

        System.out.println(
                "--------------------------------------------------------------------------"
        );

        for (Instrument instrument : GLOBAL_INSTRUMENTS) {

            testHistorical(
                    instrument,
                    from,
                    to
            );
        }
    }

    @Test
    void testHistoricalDepth() {

        LocalDate to = LocalDate.now();

        testHistoricalPeriod(
                "1 MONTH",
                to.minusMonths(1),
                to
        );

        testHistoricalPeriod(
                "1 YEAR",
                to.minusYears(1),
                to
        );

        testHistoricalPeriod(
                "5 YEARS",
                to.minusYears(5),
                to
        );

        testHistoricalPeriod(
                "10 YEARS",
                to.minusYears(10),
                to
        );
    }

    private void testHistoricalPeriod(
            String period,
            LocalDate from,
            LocalDate to
    ) {

        System.out.println();
        System.out.println(
                "--------------- " + period + " ----------------"
        );

        System.out.printf(
                "%-20s %-30s %-10s %-10s%n",
                "SYMBOL",
                "INSTRUMENT KEY",
                "CANDLES",
                "RESULT"
        );

        System.out.println(
                "--------------------------------------------------------------------------"
        );

        for (Instrument instrument : GLOBAL_INSTRUMENTS) {

            testHistorical(
                    instrument,
                    from,
                    to
            );
        }
    }

    private void testHistorical(
            Instrument instrument,
            LocalDate from,
            LocalDate to
    ) {

        try {

            List<Candle> candles =
                    provider.getHistoricalCandles(
                            instrument.instrumentKey(),
                            Timeframe.D1,
                            from,
                            to
                    );

            int count = candles == null ? 0 : candles.size();

            System.out.printf(
                    "%-20s %-30s %-10d %-10s%n",
                    instrument.symbol(),
                    instrument.instrumentKey(),
                    count,
                    count > 0 ? "PASS" : "FAIL"
            );

        } catch (Exception e) {

            System.out.printf(
                    "%-20s %-30s %-10s %-10s%n",
                    instrument.symbol(),
                    instrument.instrumentKey(),
                    "-",
                    "FAIL"
            );

            System.out.println(
                    "  ERROR: " + e.getMessage()
            );
        }
    }
}