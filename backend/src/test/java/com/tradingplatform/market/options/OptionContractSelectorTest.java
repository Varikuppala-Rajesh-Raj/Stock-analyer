package com.tradingplatform.market.options;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OptionContractSelectorTest {
    private final OptionContractSelector selector = new OptionContractSelector(
            java.math.BigDecimal.valueOf(3), java.math.BigDecimal.valueOf(100),
            java.math.BigDecimal.valueOf(1000), java.math.BigDecimal.ONE,
            java.math.BigDecimal.valueOf(.25), java.math.BigDecimal.valueOf(.75));

    @Test
    void selectsHighestRankedLiquidContractForRequestedSide() throws Exception {
        var chain = new ObjectMapper().readTree("""
                {"data":[
                  {"strike_price":22500,"underlying_spot_price":22505,
                   "call_options":{"instrument_key":"NSE_FO|CE22500","market_data":{"ltp":100,"bid_price":99,"ask_price":101,"volume":5000,"oi":50000,"change_oi":200},
                    "option_greeks":{"delta":0.51}},
                   "put_options":{"instrument_key":"NSE_FO|PE22500","market_data":{"ltp":95,"bid_price":94,"ask_price":96,"volume":5000,"oi":50000},
                    "option_greeks":{"delta":-0.49}}},
                  {"strike_price":22550,"underlying_spot_price":22505,
                   "call_options":{"instrument_key":"NSE_FO|CE22550","market_data":{"ltp":80,"bid_price":70,"ask_price":90,"volume":9000,"oi":90000},
                    "option_greeks":{"delta":0.35}},
                   "put_options":{"instrument_key":"NSE_FO|PE22550","market_data":{"ltp":120,"bid_price":119,"ask_price":121,"volume":4000,"oi":40000},
                    "option_greeks":{"delta":-0.55}}}
                ]}
                """);

        var call = selector.select(chain, "CE").orElseThrow();
        var put = selector.select(chain, "PE").orElseThrow();

        assertEquals("NSE_FO|CE22500", call.instrumentKey());
        assertEquals("NSE_FO|PE22500", put.instrumentKey());
        assertEquals("CE", call.optionType());
        assertTrue(call.spreadPercent().doubleValue() < 3);
    }

    @Test
    void rejectsCandidatesWithMissingQuotesOrInsufficientLiquidity() throws Exception {
        var chain = new ObjectMapper().readTree("""
                {"data":[
                  {"strike_price":22500,"underlying_spot_price":22500,
                   "call_options":{"instrument_key":"NSE_FO|WIDE","market_data":{"ltp":100,"bid_price":90,"ask_price":110,"volume":5000,"oi":50000},
                    "option_greeks":{"delta":0.5}},
                   "put_options":{"instrument_key":"NSE_FO|THIN","market_data":{"ltp":100,"bid_price":99,"ask_price":101,"volume":2,"oi":4},
                    "option_greeks":{"delta":-0.5}}}
                ]}
                """);

        assertTrue(selector.select(chain, "CE").isEmpty());
        assertTrue(selector.select(chain, "PE").isEmpty());
    }

    @Test
    void rejectsUnsupportedOptionSide() {
        assertThrows(IllegalArgumentException.class, () -> selector.select(null, "CALL"));
    }
}
