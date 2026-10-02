package com.tradingplatform.market.nse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NseFuturesContractsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void discoversNearestAndNextMonthlyExpiryFromNseDates() throws Exception {
        var dates = mapper.readTree("[\"29-Sep-2026\",\"06-Oct-2026\",\"13-Oct-2026\",\"20-Oct-2026\",\"27-Oct-2026\",\"24-Nov-2026\"]");

        assertEquals(List.of(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 24)),
                NseFuturesContracts.monthlyExpiries(dates, LocalDate.of(2026, 9, 30)));
    }

    @Test
    void constructsTheNseIdentifierAndValidatesQuoteFields() throws Exception {
        LocalDate expiry = LocalDate.of(2026, 10, 27);
        var response = mapper.readTree("""
                {"data":[{"identifier":"FUTIDXNIFTY27-10-2026XX0.00","instrumentType":"FUTIDX",
                "underlying":"NIFTY","expiryDate":"27-Oct-2026","lastPrice":22893.9}]}
                """);

        assertEquals("FUTIDXNIFTY27-10-2026XX0.00", NseFuturesContracts.identifier(expiry));
        assertTrue(NseFuturesContracts.isNiftyFutureQuote(response, expiry));
        assertFalse(NseFuturesContracts.isNiftyFutureQuote(mapper.readTree("{\"data\":[]}"), expiry));
    }
}