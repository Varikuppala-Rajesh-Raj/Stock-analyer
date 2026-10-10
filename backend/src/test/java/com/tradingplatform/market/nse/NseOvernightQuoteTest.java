package com.tradingplatform.market.nse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tradingplatform.market.Quote;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NseOvernightQuoteTest {

    @Test
    void usesCurrencyQuoteAndParsesProviderTimestamp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.nseindia.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://www.nseindia.com/"))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://www.nseindia.com/api/quote-currency?key=INR"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "symbol": "USDINR",
                            "lastPrice": "83.2500",
                            "previousClose": "83.1000",
                            "netChange": "0.1500",
                            "lastUpdateTime": "08-Oct-2026 15:40:00"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Quote quote = new NseMarketDataService(builder.build()).overnightNseQuote("USD_INR");

        assertEquals("NSE_API|USDINR", quote.instrumentKey());
        assertEquals("83.2500", quote.price().toPlainString());
        assertEquals("0.1500", quote.change().toPlainString());
        assertEquals("2026-10-08T10:10:00Z", quote.timestamp().toString());
        server.verify();
    }

    @Test
    void fallsBackToMatchingCommodityContractWhenQuoteEndpointFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.nseindia.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://www.nseindia.com/"))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://www.nseindia.com/api/quotes-commodity-derivatives-main"
                + "?category=Energy&symbol=BRCRUDEOIL"))
                .andRespond(withBadRequest());
        server.expect(requestTo("https://www.nseindia.com/json/quotes/commodity-all-contracts.json"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "symbol": "GOLD",
                              "lastPrice": "71000",
                              "netChange": "25",
                              "timestamp": "08-Oct-2026 15:40:00"
                            },
                            {
                              "symbol": "BRCRUDEOIL",
                              "lastPrice": "6200",
                              "netChange": "-35",
                              "timestamp": "08-Oct-2026 15:40:00"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Quote quote = new NseMarketDataService(builder.build()).overnightNseQuote("BRENT");

        assertEquals("NSE_API|BRCRUDEOIL", quote.instrumentKey());
        assertEquals("6200", quote.price().toPlainString());
        assertEquals("-35", quote.change().toPlainString());
        assertTrue(quote.timestamp().toString().startsWith("2026-10-08T10:10:00"));
        server.verify();
    }

    @Test
    void doesNotTreatAnotherCommodityContractAsBrent() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.nseindia.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://www.nseindia.com/"))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://www.nseindia.com/api/quotes-commodity-derivatives-main"
                + "?category=Energy&symbol=BRCRUDEOIL"))
                .andRespond(withBadRequest());
        server.expect(requestTo("https://www.nseindia.com/json/quotes/commodity-all-contracts.json"))
                .andRespond(withSuccess("""
                        {"data":[{"symbol":"GOLD","lastPrice":"71000","netChange":"25"}]}
                        """, MediaType.APPLICATION_JSON));

        org.springframework.web.server.ResponseStatusException error =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> new NseMarketDataService(builder.build()).overnightNseQuote("BRENT"));

        assertTrue(error.getReason().contains("no usable BRCRUDEOIL quote"));
        server.verify();
    }

    @Test
    void discoversNearestActiveGoldFuturesContractAndExcludesOptions() {
        String expiry = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(21)
                .format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.nseindia.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://www.nseindia.com/"))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://www.nseindia.com/api/quotes-commodity-master-filter?symbol=GOLD"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"symbol":"GOLD","instrumentType":"OPTFUT","expiryDate":"%s",
                             "optionType":"CE","strikePrice":"125100.00"},
                            {"symbol":"GOLD","instrumentType":"FUTCOM","expiryDate":"01-Jan-2020"},
                            {"symbol":"GOLD","instrumentType":"FUTCOM","expiryDate":"%s"},
                            {"symbol":"GOLD","instrumentType":"FUTCOM","expiryDate":"31-Dec-2027"}
                          ]
                        }
                        """.formatted(expiry, expiry), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.nseindia.com/api/quotes-commodity-derivatives-main"
                + "?category=Bullion&symbol=GOLD&instrumentType=FUTCOM&expiryDate=" + expiry))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"symbol":"GOLD","instrumentType":"OPTFUT","expiryDate":"%s",
                             "optionType":"CE","strikePrice":"125100.00","lastPrice":"99999",
                             "netChange":"50","lastUpdateTime":"08-Oct-2026 15:40:00"},
                            {"symbol":"GOLD","instrumentType":"FUTCOM","expiryDate":"%s",
                             "lastPrice":"71000","previousClose":"70975",
                             "lastUpdateTime":"08-Oct-2026 15:40:00"}
                          ]
                        }
                        """.formatted(expiry, expiry), MediaType.APPLICATION_JSON));

        Quote quote = new NseMarketDataService(builder.build()).overnightNseQuote("GOLD");

        assertEquals("NSE_COM|GOLD|" + expiry, quote.instrumentKey());
        assertEquals("71000", quote.price().toPlainString());
        assertEquals("25", quote.change().toPlainString());
        assertTrue(quote.timestamp().toString().startsWith("2026-10-08T10:10:00"));
        server.verify();
    }

    @Test
    void rejectsGoldWhenMasterHasOnlyOptionsOrExpiredFutures() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.nseindia.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://www.nseindia.com/"))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://www.nseindia.com/api/quotes-commodity-master-filter?symbol=GOLD"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"symbol":"GOLD","instrumentType":"OPTFUT","expiryDate":"31-Dec-2027"},
                          {"symbol":"GOLD","instrumentType":"FUTCOM","expiryDate":"01-Jan-2020"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        org.springframework.web.server.ResponseStatusException error =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> new NseMarketDataService(builder.build()).overnightNseQuote("GOLD"));

        assertTrue(error.getReason().contains("no usable GOLD quote"));
        server.verify();
    }
}
